package com.devcool.adapters.in.websocket.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No client frame for {@code idleTimeout} → Tomcat closes with 1001 (P4-T03). */
@ConfigurationProperties(prefix = "devcool.ws")
public record WsIdleProps(Duration idleTimeout) {

  public WsIdleProps {
    WsSendLimitsProps.requirePositive(idleTimeout, "devcool.ws.idle-timeout");
  }
}
