package com.devcool.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One pgvector container shared by all ITs, started once per JVM (ADR-0009). Reused across runs
 * only if enabled in {@code ~/.testcontainers.properties}, so don't assume an empty database.
 */
public interface PostgresTestContainer {

  /**
   * {@code pgvector/pgvector:pg16}, pinned by digest so every run (and the CI image cache) uses the
   * same image. Bump with {@code docker buildx imagetools inspect pgvector/pgvector:pg16}.
   */
  DockerImageName PGVECTOR =
      DockerImageName.parse(
              "pgvector/pgvector@sha256:7b822b0aac60967beb1ea5e576b8602c94c300a157d187f385ae3e0da199b90a")
          .asCompatibleSubstituteFor("postgres");

  @ServiceConnection PostgreSQLContainer<?> POSTGRES = startedPostgres();

  private static PostgreSQLContainer<?> startedPostgres() {
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR).withReuse(true);
    postgres.start();
    return postgres;
  }
}
