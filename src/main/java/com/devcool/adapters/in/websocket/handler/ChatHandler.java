package com.devcool.adapters.in.websocket.handler;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.SendAck;
import com.devcool.adapters.in.websocket.dto.ServerFrame;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.SessionRegistry;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.WsSubscribeUseCase;
import com.devcool.domain.chat.port.in.WsUnsubscribeUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.common.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ChatHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;

  private final SessionRegistry sessionRegistry;
  private final ConnectionRegistryPort connectionRegistryPort;
  private final WsSubscribeUseCase subscribeUseCase;
  private final WsUnsubscribeUseCase unsubscribeUseCase;
  private final SendMessageUseCase sendMessageUseCase;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessionRegistry.put(session);
    Long userId = getUserId(session);
    String connectionId = session.getId();
    connectionRegistryPort.registerConnection(connectionId, userId);
  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    String connectionId = session.getId();
    Long userId = getUserId(session);

    ClientFrame clientFrame;
    try {
      clientFrame = objectMapper.readValue(message.getPayload(), ClientFrame.class);
    } catch (JsonProcessingException ex) {
      session.sendMessage(
          new TextMessage(
              objectMapper.writeValueAsString(
                  new ServerFrame(FrameType.ERROR, null, "Invalid JSON"))));
      return;
    }

    if (Objects.isNull(clientFrame.action())) {
      session.sendMessage(
          new TextMessage(
              objectMapper.writeValueAsString(
                  new ServerFrame(FrameType.ERROR, clientFrame.clientMsgId(), "Missing action"))));
      return;
    }

    switch (clientFrame.action()) {
      case FrameType.PING -> {
        session.sendMessage(
            new TextMessage(
                objectMapper.writeValueAsString(
                    new ServerFrame(FrameType.ACK, clientFrame.clientMsgId(), "PONG"))));
      }
      case FrameType.SUBSCRIBE -> {
        subscribeUseCase.subscribe(
            new SubscribeCommand(connectionId, userId, clientFrame.channelId()));
        sendAck(session, clientFrame.clientMsgId(), "Subscribe successfully");
      }
      case FrameType.UNSUBSCRIBE -> {
        if (Objects.isNull(clientFrame.channelId())) {
          sendError(session, clientFrame.clientMsgId(), "Missing channelId");
          return;
        }
        unsubscribeUseCase.unsubscribe(
            new UnsubscribeCommand(connectionId, clientFrame.channelId()));
        sendAck(session, clientFrame.clientMsgId(), "Unsubscribe successfully");
      }
      case FrameType.SEND_MESSAGE -> {
        UUID clientMsgId = parseClientMsgId(clientFrame.clientMsgId());
        if (Objects.isNull(clientMsgId)) {
          // Don't reflect an arbitrary client string back.
          sendError(session, null, "Invalid clientMsgId");
          return;
        }
        SavedMessage saved;
        try {
          saved =
              sendMessageUseCase.sendMessage(
                  new SendMessageCommand(
                      connectionId,
                      userId,
                      clientFrame.channelId(),
                      clientFrame.contentType(),
                      clientFrame.content(),
                      clientMsgId,
                      clientFrame.replyToId()));
        } catch (DomainException ex) {
          // A rejected send is a frame-level error: report it, keep the socket open.
          sendError(session, clientMsgId.toString(), ex.getErrorCode().code());
          return;
        }

        session.sendMessage(
            new TextMessage(
                objectMapper.writeValueAsString(
                    new ServerFrame(
                        FrameType.ACK,
                        clientMsgId.toString(),
                        new SendAck(saved.id(), saved.seq(), saved.createdTime())))));
      }
      default -> {
        session.sendMessage(
            new TextMessage(
                objectMapper.writeValueAsString(
                    new ServerFrame(
                        FrameType.ERROR, clientFrame.clientMsgId(), "Unsupported action"))));
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String connectionId = session.getId();
    sessionRegistry.remove(connectionId);
    connectionRegistryPort.removeConnection(connectionId);
  }

  /** The idempotency key in canonical UUID form, or null when it is missing or malformed. */
  private static UUID parseClientMsgId(String raw) {
    // UUID.fromString accepts short forms like "1-1-1-1-1"; only the 36-char form is a client key.
    if (Objects.isNull(raw) || raw.length() != 36) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private void sendAck(WebSocketSession session, String clientMsgId, String description)
      throws IOException {
    session.sendMessage(
        new TextMessage(
            objectMapper.writeValueAsString(
                new ServerFrame(FrameType.ACK, clientMsgId, Map.of("description", description)))));
  }

  private void sendError(WebSocketSession session, String clientMsgId, Object data)
      throws IOException {
    session.sendMessage(
        new TextMessage(
            objectMapper.writeValueAsString(new ServerFrame(FrameType.ERROR, clientMsgId, data))));
  }

  private Long getUserId(WebSocketSession session) {
    return (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
  }
}
