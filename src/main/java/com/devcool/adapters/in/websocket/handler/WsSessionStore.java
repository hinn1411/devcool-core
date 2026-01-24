package com.devcool.adapters.in.websocket.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionStore {

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  private void put(WebSocketSession session) {
    sessions.put(session.getId(), session);
  }

  private void String(String connectionId) {
    sessions.remove(connectionId);
  }

  private WebSocketSession get(String connectionId) {
    return sessions.get(connectionId);
  }
}
