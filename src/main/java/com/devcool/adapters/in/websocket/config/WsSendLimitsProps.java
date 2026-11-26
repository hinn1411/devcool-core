package com.devcool.adapters.in.websocket.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Per-session send limits (P4-T01, 03-chat-system-design §8).
 *
 * @param timeLimit a send stuck longer than this closes the session (checked by the next send)
 * @param bufferSizeLimit queued bytes beyond this close the session
 * @param blockingTimeout Tomcat's own timeout for one blocking socket write
 */
@ConfigurationProperties(prefix = "devcool.ws.send")
public record WsSendLimitsProps(
    Duration timeLimit, DataSize bufferSizeLimit, Duration blockingTimeout) {}
