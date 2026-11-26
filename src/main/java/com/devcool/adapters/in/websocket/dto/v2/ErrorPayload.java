package com.devcool.adapters.in.websocket.dto.v2;

/** Payload of {@code NACK} and {@code ERROR}. */
public record ErrorPayload(NackCode code, String message) {}
