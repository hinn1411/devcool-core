package com.devcool.adapters.in.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Legacy v1 frames are accepted during P4 only (ADR-0005 rule 5); P4-T17 removes them. */
@ConfigurationProperties(prefix = "devcool.ws.protocol")
public record WsProtocolProps(@DefaultValue("true") boolean legacyV1Enabled) {}
