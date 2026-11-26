package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.dto.MessagePush;
import com.devcool.domain.chat.model.MessageEvent;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** Outbound side of the WS adapter; shares {@link SessionRegistry}. Replaced in P4 (ADR-0006). */
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
    } catch (JsonProcessingException ex) {
      // A bug in the payload, not the transport: keep the connection.
      log.error("Push of message {} could not be serialized", event.messageId(), ex);
    } catch (IOException ex) {
      // At-most-once: the client reconnects and catches up by seq.
      log.warn("Push of message {} to {} failed, closing", event.messageId(), connectionId, ex);
      closeQuietly(session);
    } catch (IllegalStateException ex) {
      log.warn("Push of message {} to {} failed", event.messageId(), connectionId, ex);
    }
  }

  private static void closeQuietly(WebSocketSession session) {
    try {
      session.close(CloseStatus.SESSION_NOT_RELIABLE);
    } catch (IOException ex) {
      log.debug("Close of {} failed", session.getId(), ex);
    }
  }
}
