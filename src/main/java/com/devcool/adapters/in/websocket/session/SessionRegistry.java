package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.config.WsIdleProps;
import com.devcool.adapters.in.websocket.config.WsSendLimitsProps;
import jakarta.websocket.Session;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator.OverflowStrategy;

/** This node's sessions, each wrapped once for serialized, bounded sends (P4-T01). */
@Component
@RequiredArgsConstructor
public class SessionRegistry {

  /** A {@code Long} in ms; Tomcat's default is 20 s. */
  static final String TOMCAT_BLOCKING_SEND_TIMEOUT =
      "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";

  /** A {@code Long} in ms (P4-T03); only client frames reset it, so pushes can't keep it alive. */
  static final String TOMCAT_READ_IDLE_TIMEOUT = "org.apache.tomcat.websocket.READ_IDLE_TIMEOUT_MS";

  private final WsSendLimitsProps limits;
  private final WsIdleProps idle;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  /**
   * Wraps {@code raw} and stores the wrapper: every later write must go through the returned
   * session. Over a limit, the decorator throws {@code SessionLimitExceededException}; the writer
   * closes the session (4500).
   */
  public WebSocketSession register(WebSocketSession raw) {
    bound(raw);
    WebSocketSession safe =
        new ConcurrentWebSocketSessionDecorator(
            raw,
            Math.toIntExact(limits.timeLimit().toMillis()),
            Math.toIntExact(limits.bufferSizeLimit().toBytes()),
            OverflowStrategy.TERMINATE);
    sessions.put(raw.getId(), safe);
    return safe;
  }

  public void remove(String connectionId) {
    sessions.remove(connectionId);
  }

  public WebSocketSession get(String connectionId) {
    return sessions.get(connectionId);
  }

  /**
   * Bounds a stuck write (the decorator only trips on the next send) and closes a silent client.
   */
  private void bound(WebSocketSession raw) {
    if (raw instanceof NativeWebSocketSession nativeAware) {
      Session nativeSession = nativeAware.getNativeSession(Session.class);
      if (Objects.nonNull(nativeSession)) {
        Map<String, Object> properties = nativeSession.getUserProperties();
        properties.put(TOMCAT_BLOCKING_SEND_TIMEOUT, limits.blockingTimeout().toMillis());
        properties.put(TOMCAT_READ_IDLE_TIMEOUT, idle.idleTimeout().toMillis());
      }
    }
  }
}
