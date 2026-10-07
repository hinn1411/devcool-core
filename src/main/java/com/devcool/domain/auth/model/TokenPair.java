package com.devcool.domain.auth.model;

/**
 * A freshly issued token pair. {@code refreshJti} is the refresh token's id, so the application can
 * record the token without parsing it.
 */
public record TokenPair(String accessToken, String refreshToken, String refreshJti) {}
