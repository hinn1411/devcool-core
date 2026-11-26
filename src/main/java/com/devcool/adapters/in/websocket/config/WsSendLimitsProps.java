package com.devcool.adapters.in.websocket.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Per-session send limits (P4-T01); {@code blockingTimeout} is Tomcat's per-write timeout. */
@ConfigurationProperties(prefix = "devcool.ws.send")
public record WsSendLimitsProps(
    Duration timeLimit, DataSize bufferSizeLimit, Duration blockingTimeout) {}
