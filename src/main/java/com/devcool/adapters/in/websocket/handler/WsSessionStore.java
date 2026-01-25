package com.devcool.adapters.in.websocket.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionStore {

  public final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  public void put(WebSocketSession session) {
    sessions.put(session.getId(), session);
  }

  public void remove(String connectionId) {
    sessions.remove(connectionId);
  }

  public WebSocketSession get(String connectionId) {
    return sessions.get(connectionId);
  }
}
