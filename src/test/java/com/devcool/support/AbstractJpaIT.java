package com.devcool.support;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for persistence integration tests: JPA, Flyway and the repositories only, against the shared
 * Postgres container. Import the adapter and mappers under test in the subclass.
 *
 * <p>Each test runs in a transaction that is rolled back, so INSERTs are only queued until a flush.
 * Use {@code saveAndFlush} or {@code flush()} + {@code clear()} when the assertion is about what
 * the database stored (learning/12 §6).
 *
 * <p>No {@code @AutoConfigureTestDatabase(replace = NONE)}: since Boot 3.4 the default {@code
 * NON_TEST} keeps a {@code @ServiceConnection} datasource.
 */
@DataJpaTest
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
public abstract class AbstractJpaIT {}
