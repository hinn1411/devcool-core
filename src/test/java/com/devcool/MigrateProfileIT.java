package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.support.PostgresTestContainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Boots {@code ecs,migrate} like the ECS task does, each test on a fresh database (P2-T05). */
class MigrateProfileIT {

  // Same test keys as application-test.properties.
  private static final String JWT_ACCESS = "7poLGgBQYOjm5si8gg16ww/8rLeZ5NxF/m/Df9i4Dm4=";
  private static final String JWT_REFRESH = "HPQMi5tro5vb4sTgZcNaGydQBSoAqqQzIGgdFmOYLAQ=";

  @Test
  void migrate_onAnEmptyDatabase_appliesEveryMigrationAndStartsNoWebServer() {
    String url = freshDatabase();

    try (ConfigurableApplicationContext ctx = runMigrate(url)) {
      assertThat(ctx).isNotInstanceOf(WebServerApplicationContext.class);

      List<Boolean> history =
          new JdbcTemplate(ctx.getBean(DataSource.class))
              .queryForList(
                  "select success from flyway_schema_history order by installed_rank",
                  Boolean.class);
      assertThat(history).hasSizeGreaterThanOrEqualTo(2).containsOnly(true);
    }
  }

  @Test
  void migrate_whenAMigrationFails_stopsStartup() {
    String url = freshDatabase();

    // Keep the real migrations so only V9000 can fail.
    assertThatThrownBy(
            () ->
                runMigrate(
                        url,
                        "--spring.flyway.locations=classpath:db/migration,classpath:db/broken-migration")
                    .close())
        .as("a failed migration must fail startup, so main exits != 0")
        .hasStackTraceContaining("V9000__broken.sql");

    // V9000 rolled back; V1/V2 stay applied.
    var pg = PostgresTestContainer.POSTGRES;
    List<String> applied =
        new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()))
            .queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class);
    assertThat(applied).startsWith("1", "2").doesNotContain("9000");
  }

  /** Same profiles and env var names as the ECS task. */
  private static ConfigurableApplicationContext runMigrate(String jdbcUrl, String... extraArgs) {
    String[] args =
        Stream.concat(
                Stream.of(
                    "--spring.profiles.active=ecs,migrate",
                    "--SPRING_DATASOURCE_URL=" + jdbcUrl,
                    "--SPRING_DATASOURCE_USERNAME=" + PostgresTestContainer.POSTGRES.getUsername(),
                    "--SPRING_DATASOURCE_PASSWORD=" + PostgresTestContainer.POSTGRES.getPassword(),
                    "--JWT_ACCESS_SECRET=" + JWT_ACCESS,
                    "--JWT_REFRESH_SECRET=" + JWT_REFRESH),
                Stream.of(extraArgs))
            .toArray(String[]::new);
    return SpringApplication.run(DevCoolApplication.class, args);
  }

  /** Empty database in the shared container; returns its JDBC URL. */
  private static String freshDatabase() {
    String name = "migrate_it_" + UUID.randomUUID().toString().replace("-", "");
    var pg = PostgresTestContainer.POSTGRES;
    try (Connection c =
        DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
      c.createStatement().execute("create database " + name);
    } catch (SQLException e) {
      throw new IllegalStateException("could not create " + name, e);
    }
    return pg.getJdbcUrl().replace("/" + pg.getDatabaseName(), "/" + name);
  }
}
