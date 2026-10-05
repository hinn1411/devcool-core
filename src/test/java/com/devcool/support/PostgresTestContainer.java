package com.devcool.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One pgvector container shared by all ITs, started once per JVM (ADR-0009). Reused across runs
 * only if enabled in {@code ~/.testcontainers.properties}, so don't assume an empty database.
 */
public interface PostgresTestContainer {

  DockerImageName PGVECTOR =
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

  @ServiceConnection PostgreSQLContainer<?> POSTGRES = startedPostgres();

  private static PostgreSQLContainer<?> startedPostgres() {
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR).withReuse(true);
    postgres.start();
    return postgres;
  }
}
