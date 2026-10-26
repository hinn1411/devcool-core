package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.devcool.support.AbstractIntegrationTest;
import com.devcool.support.PostgresTestContainer;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The app boots on pgvector with no env vars; Flyway builds the schema and validate passes. */
class DevCoolApplicationIT extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mockMvc;

  @Test
  void contextLoads_againstTheSharedContainer() throws Exception {
    assertThat(mockMvc).isNotNull();
    assertThat(dataSource.unwrap(HikariDataSource.class).getJdbcUrl())
        .isEqualTo(PostgresTestContainer.POSTGRES.getJdbcUrl());
  }

  @Test
  void flyway_appliedTheBaselineAndIndexMigrations_allSuccessfully() {
    List<Tuple> history =
        jdbc.query(
            "select version, success from flyway_schema_history order by installed_rank",
            (rs, rowNum) -> tuple(rs.getString("version"), rs.getBoolean("success")));

    // startsWith: new migrations must not break this test.
    assertThat(history).startsWith(tuple("1", true), tuple("2", true), tuple("3", true));
    assertThat(history).allSatisfy(row -> assertThat(row.toList().get(1)).isEqualTo(true));
  }

  @Test
  void schema_hasNoIntegerPrimaryOrForeignKeyColumns_afterV3() {
    // Every column that takes part in a PK or FK constraint, with its type
    List<String> integerKeyColumns =
        jdbc.queryForList(
            """
            select distinct c.table_name || '.' || c.column_name
              from information_schema.key_column_usage k
              join information_schema.table_constraints t
                on t.constraint_name = k.constraint_name and t.table_schema = k.table_schema
              join information_schema.columns c
                on c.table_schema = k.table_schema
               and c.table_name = k.table_name
               and c.column_name = k.column_name
             where k.table_schema = current_schema()
               and k.table_name <> 'flyway_schema_history' -- Flyway's own table, not ours
               and t.constraint_type in ('PRIMARY KEY', 'FOREIGN KEY')
               and c.data_type = 'integer'
            """,
            String.class);

    assertThat(integerKeyColumns).as("V3__ids_to_bigint.sql").isEmpty();
  }

  @Test
  void schema_hasTheMessageHistoryIndexFromV2() {
    assertThat(exists("select 1 from pg_indexes where indexname = ?", "ix_message_channel_id_id"))
        .as("index from V2__message_channel_id_index.sql")
        .isTrue();
  }

  @Test
  void database_isThePgvectorImage() {
    // Available, not created (that's P8-T01).
    assertThat(exists("select 1 from pg_available_extensions where name = ?", "vector"))
        .as("pgvector extension available in the image (ADR-0009)")
        .isTrue();
  }

  private boolean exists(String sql, Object... args) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("select exists(" + sql + ")", Boolean.class, args));
  }
}
