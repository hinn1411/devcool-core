package com.devcool.domain.auth.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Getter
@Setter
@Builder
public class RefreshToken {
    String jti;
    Integer userId;
    Instant issuedTime;
    Instant expiredTime;
    Instant consumedTime;
}
