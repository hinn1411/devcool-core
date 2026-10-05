package com.devcool.support;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for persistence ITs: JPA slice + shared Postgres. Tests roll back, so flush before asserting
 * on stored data.
 */
@DataJpaTest
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
public abstract class AbstractJpaIT {}
