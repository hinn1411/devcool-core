package com.devcool.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The one Postgres container every integration test shares. It starts once per JVM, when this
 * interface is first initialized, and is never stopped by a test: Ryuk removes it when the JVM
 * exits. A fixed container keeps a fixed JDBC URL, which lets Spring reuse its cached contexts.
 *
 * <p>The image is the one local and Aurora run (ADR-0009). {@code withReuse(true)} keeps it alive
 * across runs only when {@code testcontainers.reuse.enable=true} is set in {@code
 * ~/.testcontainers.properties}; CI never sets it. Tests must not assume an empty database.
 *
 * <p>Imported with {@code @ImportTestcontainers(PostgresTestContainer.class)}; see the base
 * classes.
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
