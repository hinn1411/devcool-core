package com.devcool.domain.auth.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RefreshToken {
  private String jti;
  private Integer userId;
  private Instant issuedTime;
  private Instant expiredTime;
  private Instant consumedTime;
}
