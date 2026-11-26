package com.devcool.adapters.in.websocket.dto.v2;

/** {@code ACK} of a {@code READ}: the marker after the call. */
public record ReadAckPayload(long seq) {}
