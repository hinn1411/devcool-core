package com.devcool.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for full-stack integration tests: the whole application context, MockMvc through the real
 * security filter chain, and the shared Postgres container.
 *
 * <p>Every IT that extends this shares one cached Spring context. Don't add configuration
 * annotations ({@code @MockitoBean}, {@code @TestPropertySource}, extra profiles) in a subclass:
 * each one creates another context (learning/12 §4). Name subclasses {@code *IT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
public abstract class AbstractIntegrationTest {}
