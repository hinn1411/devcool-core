package com.devcool.adapters.out.realtime.immemory;

import com.devcool.adapters.in.websocket.handler.WsSessionStore;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

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
