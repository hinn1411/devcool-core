package com.devcool.domain.user.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserTest {

  @ParameterizedTest(name = "dbVersion: {0}, rawVersion: {1}, expected: {2}")
  @CsvSource({
    "1, 1, true",
    "2, 1, false",
    "5, 1, false",
    "1, 2, false" // rawVersion issued by server must <= dbVersion
  })
  void isTokenVersionValid_validOnlyRawEqualDbVersion(
      int dbTokenVersion, int rawTokenVersion, boolean expected) {
    User user = User.builder().tokenVersion(dbTokenVersion).build();
    assertThat(user.isTokenVersionValid(rawTokenVersion)).isEqualTo(expected);
  }

  // User created before table has version column
  @Test
  void isTokenVersionValid_nullDbTokenVersion_rejects() {
    User user = User.builder().tokenVersion(null).build();
    assertThat(user.isTokenVersionValid(1)).isFalse();
  }

  @Test
  void isTokenVersionValid_nullRawTokenVersion_rejects() {
    User user = User.builder().tokenVersion(1).build();
    assertThat(user.isTokenVersionValid(null)).isFalse();
  }
}
