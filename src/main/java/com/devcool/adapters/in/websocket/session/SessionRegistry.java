package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.config.WsSendLimitsProps;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class SessionRegistry {

  private final WsSendLimitsProps limits;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  public void put(WebSocketSession session) {
    sessions.put(session.getId(), session);
  }

  /** Wraps {@code raw} for safe concurrent, bounded sends and stores the wrapper. */
  public WebSocketSession register(WebSocketSession raw) {
    throw new UnsupportedOperationException();
  }

  public void remove(String connectionId) {
    sessions.remove(connectionId);
  }

  public WebSocketSession get(String connectionId) {
    return sessions.get(connectionId);
  }
}
