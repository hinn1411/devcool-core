package com.devcool.support;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Base for migration ITs: a fresh database per test, seeded at version N, then migrated on. No
 * Spring context; Flyway is configured here so every IT matches the app's settings.
 */
public abstract class AbstractMigrationIT {

  protected String url;
  protected JdbcTemplate jdbc;

  protected void openFreshDatabase(String prefix) {
    url = PostgresTestContainer.freshDatabase(prefix);
    var pg = PostgresTestContainer.POSTGRES;
    jdbc = new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()));
  }

  /** {@code null} migrates to the latest version. */
  protected void migrateTo(String target) {
    var pg = PostgresTestContainer.POSTGRES;
    // As application.properties: without it, CREATE INDEX CONCURRENTLY (V8) waits forever on
    // Flyway's own lock transaction.
    var config =
        Flyway.configure()
            .dataSource(url, pg.getUsername(), pg.getPassword())
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"));
    if (target != null) {
      config.target(target);
    }
    config.load().migrate();
  }
}
