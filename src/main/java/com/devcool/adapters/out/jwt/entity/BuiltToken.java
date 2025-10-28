package com.devcool.adapters.out.jwt.entity;

import java.time.Instant;

public record BuiltToken(String raw, Instant expiredTime) {}