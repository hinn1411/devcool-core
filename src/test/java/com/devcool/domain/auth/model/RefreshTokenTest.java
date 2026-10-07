package com.devcool.domain.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  @Test
  void issue_expiresSevenDaysAfterIssueAndIsNotConsumed() {
    Instant now = Instant.parse("2026-10-07T10:00:00Z");

    RefreshToken token = RefreshToken.issue(7, "jti-hash", now);

    assertThat(token.getJti()).isEqualTo("jti-hash");
    assertThat(token.getUserId()).isEqualTo(7);
    assertThat(token.getIssuedTime()).isEqualTo(now);
    assertThat(token.getExpiredTime()).isEqualTo(now.plus(Duration.ofDays(7)));
    assertThat(token.getConsumedTime()).isNull();
  }
}
