package com.devcool.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for full-stack ITs: whole context + MockMvc + shared Postgres. Subclasses add no config
 * annotations, so they all share one cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
public abstract class AbstractIntegrationTest {}
