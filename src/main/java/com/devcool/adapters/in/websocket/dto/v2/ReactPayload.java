package com.devcool.adapters.in.websocket.dto.v2;

/** {@code REACT}; {@code on} false removes the caller's reaction. */
public record ReactPayload(Long messageId, String emoji, Boolean on) {}
