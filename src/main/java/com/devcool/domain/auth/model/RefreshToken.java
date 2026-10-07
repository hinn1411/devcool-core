package com.devcool.domain.auth.model;

import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RefreshToken {

  /** How long a refresh token is honoured: the JWT expiry, the stored row and the cookie. */
  public static final Duration TTL = Duration.ofDays(7);

  private String jti;
  private Integer userId;
  private Instant issuedTime;
  private Instant expiredTime;
  private Instant consumedTime;

  public static RefreshToken issue(Integer userId, String jtiHash, Instant now) {
    return RefreshToken.builder()
        .jti(jtiHash)
        .userId(userId)
        .issuedTime(now)
        .expiredTime(now.plus(TTL))
        .build();
  }
}
