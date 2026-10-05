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

/**
 * Smoke test for the whole application: the context starts against an empty pgvector database with
 * no environment variables, Flyway builds the schema, and Hibernate's {@code validate} accepts it
 * (P1-T01's Definition of Done, automated).
 */
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

    // startsWith, not containsExactly: a new migration (V3, …) must not break this smoke test.
    assertThat(history).startsWith(tuple("1", true), tuple("2", true));
    assertThat(history).allSatisfy(row -> assertThat(row.toList().get(1)).isEqualTo(true));
  }

  @Test
  void schema_hasTheMessageHistoryIndexFromV2() {
    assertThat(exists("select 1 from pg_indexes where indexname = ?", "ix_message_channel_id_id"))
        .as("index from V2__message_channel_id_index.sql")
        .isTrue();
  }

  @Test
  void database_isThePgvectorImage() {
    // Available, not created: CREATE EXTENSION vector is P8-T01's migration.
    assertThat(exists("select 1 from pg_available_extensions where name = ?", "vector"))
        .as("pgvector extension available in the image (ADR-0009)")
        .isTrue();
  }

  private boolean exists(String sql, Object... args) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("select exists(" + sql + ")", Boolean.class, args));
  }
}
