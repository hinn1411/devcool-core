package com.devcool.adapters.out.persistence.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "REFRESH_TOKEN")
@Getter
@Setter
public class RefreshTokenEntity {
    @Id
    @Column(name = "JTI", nullable = false)
    String jti;
    @Column(name = "USER_ID", nullable = false)
    String userId;
    @Column(name = "ISSUED_TIME")
    Instant issuedTime;
    @Column(name = "EXPIRED_TIME")
    Instant expiredTime;
    @Column(name = "CONSUMED_TIME")
    Instant consumedTime;
}
