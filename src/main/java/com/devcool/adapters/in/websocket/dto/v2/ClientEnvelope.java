package com.devcool.adapters.in.websocket.dto.v2;

import com.fasterxml.jackson.databind.JsonNode;

/** A v2 client frame (ADR-0005); {@code payload} is read per type by its handler. */
public record ClientEnvelope(
    Integer v, String type, String reqId, Long channelId, JsonNode payload) {}
