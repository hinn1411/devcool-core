package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.dto.MessagePush;
import com.devcool.domain.chat.model.MessageEvent;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
  private final FrameWriter writer;

  @Override
  public void send(String connectionId, MessageEvent event) {
    WebSocketSession session = sessionRegistry.get(connectionId);
    if (Objects.isNull(session) || !session.isOpen()) {
      return;
    }
    try {
      writer.send(session, MessagePush.from(event));
    } catch (IOException | IllegalStateException ex) {
      // At-most-once: the client catches up by seq. Log, don't fail the sender's request.
      log.warn(
          "Push of message {} to {} failed: {}", event.messageId(), connectionId, ex.toString());
    }
  }
}
