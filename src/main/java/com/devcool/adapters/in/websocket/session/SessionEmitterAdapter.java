package com.devcool.adapters.in.websocket.session;

import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * The driven side of the WebSocket adapter. It lives next to {@link SessionRegistry} because both
 * directions share one transport and one session map; P4's Valkey backplane (ADR-0006) replaces it.
 */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class SessionEmitterAdapter implements RealtimeEmitterPort {

  private static final Logger log = LoggerFactory.getLogger(SessionEmitterAdapter.class);
  private final SessionRegistry sessionRegistry;
  private final ObjectMapper objectMapper;

  @Override
  public void sendMessageToConnection(String connectionId, Object payload) {
    WebSocketSession session = sessionRegistry.get(connectionId);
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
