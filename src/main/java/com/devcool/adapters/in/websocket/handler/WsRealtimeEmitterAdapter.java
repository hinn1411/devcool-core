package com.devcool.adapters.in.websocket.handler;

import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The driven side of the WebSocket adapter. It lives next to {@link WsSessionStore} because both
 * directions share one transport and one session map; P4's Valkey backplane (ADR-0006) replaces it.
 */
@Component
@RequiredArgsConstructor
public class WsRealtimeEmitterAdapter implements RealtimeEmitterPort {

  private static final Logger log = LoggerFactory.getLogger(WsRealtimeEmitterAdapter.class);
  private final WsSessionStore sessionStore;
  private final ObjectMapper objectMapper;

  @Override
  public void sendMessageToConnection(String connectionId, Object payload) {
    WebSocketSession session = sessionStore.get(connectionId);
    if (Objects.isNull(session) || !session.isOpen()) {
      return;
    }

    try {
      String json = objectMapper.writeValueAsString(payload);
      session.sendMessage(new TextMessage(json));
    } catch (Exception ignored) {
      log.info("Ignore emitting response exception!");
    }
  }
}
