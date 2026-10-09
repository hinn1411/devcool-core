package com.devcool.adapters.in.websocket.handler;

import com.devcool.adapters.in.websocket.security.WsAuthHandShakeInterceptor;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * Puts {@code userId} and {@code connectionId} into the MDC for every callback, so each log line
 * written while a frame is handled carries them as fields (P1-T15). A WS frame has no request
 * around it, so this is the only way to tie a log line to a connection.
 *
 * <p>The keys are removed in {@code finally}: Tomcat reuses threads, and a leftover value would
 * label the next connection's logs with this user. Only our keys are removed, so MDC entries set by
 * others survive. The MDC is per thread: work handed to an executor doesn't see these values.
 *
 * <p>Spring's own "Closing session due to exception" log comes from an outer decorator, after this
 * one has cleaned up, so it has no MDC fields. Its message includes the session id.
 */
public class MdcWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

  static final String USER_ID = "userId";
  static final String CONNECTION_ID = "connectionId";

  public MdcWebSocketHandlerDecorator(WebSocketHandler delegate) {
    super(delegate);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    withMdc(session, () -> super.afterConnectionEstablished(session));
  }

  @Override
  public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
      throws Exception {
    withMdc(session, () -> super.handleMessage(session, message));
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    withMdc(session, () -> super.handleTransportError(session, exception));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
      throws Exception {
    withMdc(session, () -> super.afterConnectionClosed(session, closeStatus));
  }

  private static void withMdc(WebSocketSession session, Callback callback) throws Exception {
    Object userId = session.getAttributes().get(WsAuthHandShakeInterceptor.ATTR_USER_ID);
    MDC.put(CONNECTION_ID, session.getId());
    if (Objects.nonNull(userId)) {
      MDC.put(USER_ID, userId.toString());
    }
    try {
      callback.run();
    } finally {
      MDC.remove(CONNECTION_ID);
      MDC.remove(USER_ID);
    }
  }

  @FunctionalInterface
  private interface Callback {
    void run() throws Exception;
  }
}
