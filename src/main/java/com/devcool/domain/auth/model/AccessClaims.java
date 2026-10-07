package com.devcool.domain.auth.model;

/** The claims of a verified access token that the caller's identity is built from. */
public record AccessClaims(Integer userId, Integer tokenVersion, String role) {}
