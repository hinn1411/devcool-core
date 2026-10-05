package com.devcool.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The JPA slice uses the shared container, not an embedded database. */
class JpaSliceSmokeIT extends AbstractJpaIT {

  @Autowired DataSource dataSource;

  @Test
  void jpaSlice_usesTheSharedContainer() throws Exception {
    assertThat(dataSource.unwrap(HikariDataSource.class).getJdbcUrl())
        .isEqualTo(PostgresTestContainer.POSTGRES.getJdbcUrl());
  }
}
