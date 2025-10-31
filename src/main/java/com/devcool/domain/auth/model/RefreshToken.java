package com.devcool.domain.auth.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


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
