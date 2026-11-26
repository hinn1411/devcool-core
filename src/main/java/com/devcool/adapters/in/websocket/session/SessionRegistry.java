package com.devcool.adapters.in.websocket.session;

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

/** Live sessions of this node, each wrapped once for safe concurrent, bounded sends (P4-T01). */
@Component
@RequiredArgsConstructor
public class SessionRegistry {

  /** Tomcat reads it per blocking write, as a {@code Long} in milliseconds (default 20 s). */
  static final String TOMCAT_BLOCKING_SEND_TIMEOUT =
      "org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT";

  private final WsSendLimitsProps limits;
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
   * The decorator checks its time limit only when another send arrives; this bounds the thread that
   * is stuck in the socket write itself.
   */
  private void bound(WebSocketSession raw) {
    if (raw instanceof NativeWebSocketSession nativeAware) {
      Session nativeSession = nativeAware.getNativeSession(Session.class);
      if (Objects.nonNull(nativeSession)) {
        nativeSession
            .getUserProperties()
            .put(TOMCAT_BLOCKING_SEND_TIMEOUT, limits.blockingTimeout().toMillis());
      }
    }
  }
}
