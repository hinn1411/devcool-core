package com.devcool.adapters.in.websocket.dto.v2;

import com.fasterxml.jackson.databind.JsonNode;

/** v2 client frame (ADR-0005); each handler reads its own {@code payload}. */
public record ClientEnvelope(
    Integer v, String type, String reqId, Long channelId, JsonNode payload) {}
