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
 * Adds {@code userId} and {@code connectionId} to the MDC for every WS callback (P1-T15).
 *
 * <p>Only these keys are removed, in {@code finally}, so pooled threads don't leak them to the next
 * connection. The MDC is per thread: executor work doesn't see them. Spring's "Closing session due
 * to exception" log runs after cleanup, so it lacks these fields.
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
