package com.devcool.adapters.in.websocket.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server idle close (P4-T03): no client frame for {@code idleTimeout} closes the socket. */
@ConfigurationProperties(prefix = "devcool.ws")
public record WsIdleProps(Duration idleTimeout) {}
