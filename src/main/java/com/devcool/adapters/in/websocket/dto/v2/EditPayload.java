package com.devcool.adapters.in.websocket.dto.v2;

/** {@code EDIT}; {@code expectedVersion} is the version the client last saw. */
public record EditPayload(Long messageId, String content, Integer expectedVersion) {}
