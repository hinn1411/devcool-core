package com.devcool.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the {@code @DataJpaTest} slice runs on the shared container rather than an embedded
 * database, so persistence ITs test the SQL that production runs.
 */
class JpaSliceSmokeIT extends AbstractJpaIT {

  @Autowired DataSource dataSource;

  @Test
  void jpaSlice_usesTheSharedContainer() throws Exception {
    assertThat(dataSource.unwrap(HikariDataSource.class).getJdbcUrl())
        .isEqualTo(PostgresTestContainer.POSTGRES.getJdbcUrl());
  }
}
