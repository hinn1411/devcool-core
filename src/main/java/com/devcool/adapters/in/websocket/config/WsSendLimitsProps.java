package com.devcool.adapters.in.websocket.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Per-session send limits (P4-T01); {@code blockingTimeout} is Tomcat's per-write timeout. */
@ConfigurationProperties(prefix = "devcool.ws.send")
public record WsSendLimitsProps(
    Duration timeLimit, DataSize bufferSizeLimit, Duration blockingTimeout) {

  public WsSendLimitsProps {
    requirePositive(timeLimit, "devcool.ws.send.time-limit");
    requirePositive(blockingTimeout, "devcool.ws.send.blocking-timeout");
    Objects.requireNonNull(bufferSizeLimit, "devcool.ws.send.buffer-size-limit");
    if (bufferSizeLimit.toBytes() <= 0) {
      throw new IllegalArgumentException("devcool.ws.send.buffer-size-limit must be positive");
    }
  }

  /** Tomcat reads ≤ 0 as "no limit": fail startup instead. */
  static void requirePositive(Duration value, String property) {
    Objects.requireNonNull(value, property);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(property + " must be positive");
    }
  }
}
