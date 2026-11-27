package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.dto.MessagePush;
import com.devcool.adapters.in.websocket.dto.v2.MessageDeletedPayload;
import com.devcool.adapters.in.websocket.dto.v2.MessageNewPayload;
import com.devcool.adapters.in.websocket.dto.v2.MessageUpdatedPayload;
import com.devcool.adapters.in.websocket.dto.v2.ReactionPayload;
import com.devcool.adapters.in.websocket.dto.v2.ReadReceiptPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.domain.chat.model.MessageDeletedEvent;
import com.devcool.domain.chat.model.MessageEvent;
import com.devcool.domain.chat.model.MessageUpdatedEvent;
import com.devcool.domain.chat.model.ReactionEvent;
import com.devcool.domain.chat.model.ReadReceiptEvent;
import com.devcool.domain.chat.model.RealtimeEvent;
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
  public void send(String connectionId, RealtimeEvent event) {
    WebSocketSession session = sessionRegistry.get(connectionId);
    if (Objects.isNull(session) || !session.isOpen()) {
      return;
    }
    Object push = push(session, event);
    if (Objects.isNull(push)) {
      return;
    }
    try {
      writer.send(session, push);
    } catch (JsonProcessingException ex) {
      // Payload bug, not transport: keep the connection.
      log.error("Push of {} could not be serialized", event, ex);
    } catch (IOException | RuntimeException ex) {
      // At-most-once: the client reconnects and catches up by seq.
      log.warn(
          "Push of {} in channel {} to {} failed, closing",
          event.getClass().getSimpleName(),
          event.channelId(),
          connectionId,
          ex);
      writer.closeAsync(session, CloseStatus.SESSION_NOT_RELIABLE);
    }
  }

  /** Null when the session's protocol has no shape for the event (v1: MESSAGE_NEW only). */
  private static Object push(WebSocketSession session, RealtimeEvent event) {
    if (!ProtocolVersions.isV2(session)) {
      return event instanceof MessageEvent message ? MessagePush.from(message) : null;
    }
    Long channelId = event.channelId();
    return switch (event) {
      case MessageEvent e ->
          ServerEnvelope.event("MESSAGE_NEW", channelId, MessageNewPayload.from(e));
      case MessageUpdatedEvent e ->
          ServerEnvelope.event("MESSAGE_UPDATED", channelId, MessageUpdatedPayload.from(e));
      case MessageDeletedEvent e ->
          ServerEnvelope.event("MESSAGE_DELETED", channelId, MessageDeletedPayload.from(e));
      case ReactionEvent e -> ServerEnvelope.event("REACTION", channelId, ReactionPayload.from(e));
      case ReadReceiptEvent e ->
          ServerEnvelope.event("READ_RECEIPT", channelId, ReadReceiptPayload.from(e));
    };
  }
}
