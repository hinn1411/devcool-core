package com.devcool.adapters.in.websocket.handler;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.handler.action.FrameAction;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.adapters.in.websocket.session.SessionRegistry;
import com.devcool.domain.chat.port.in.ConnectionLifecycleUseCase;
import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;
import com.devcool.domain.common.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public final class ChatHandler extends TextWebSocketHandler {

  private final ObjectMapper objectMapper;
  private final SessionRegistry sessionRegistry;
  private final ConnectionLifecycleUseCase connections;
  private final FrameWriter writer;
  private final Map<FrameType, FrameAction> actions = new EnumMap<>(FrameType.class);

  public ChatHandler(
      ObjectMapper objectMapper,
      SessionRegistry sessionRegistry,
      ConnectionLifecycleUseCase connections,
      FrameWriter writer,
      List<FrameAction> actions) {
    this.objectMapper = objectMapper;
    this.sessionRegistry = sessionRegistry;
    this.connections = connections;
    this.writer = writer;
    for (FrameAction action : actions) {
      if (Objects.nonNull(this.actions.put(action.type(), action))) {
        throw new IllegalStateException("Two actions for " + action.type());
      }
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessionRegistry.put(session);
    connections.connect(
        new ConnectCommand(session.getId(), AuthHandshakeInterceptor.userIdOf(session)));
  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    ClientFrame frame;
    try {
      frame = objectMapper.readValue(message.getPayload(), ClientFrame.class);
    } catch (JsonProcessingException ex) {
      writer.error(session, null, "Invalid JSON");
      return;
    }
    if (Objects.isNull(frame.action())) {
      writer.error(session, frame.clientMsgId(), "Missing action");
      return;
    }
    dispatch(session, frame);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String connectionId = session.getId();
    sessionRegistry.remove(connectionId);
    connections.disconnect(new DisconnectCommand(connectionId));
  }

  /** A DomainException becomes an ERROR frame: an escaping one makes Spring close the socket. */
  private void dispatch(WebSocketSession session, ClientFrame frame) throws IOException {
    FrameAction action = actions.get(frame.action());
    if (Objects.isNull(action)) {
      writer.error(session, frame.clientMsgId(), "Unsupported action");
      return;
    }
    try {
      action.handle(session, frame);
    } catch (DomainException ex) {
      writer.error(session, frame.clientMsgId(), ex.getErrorCode().code());
    }
  }
}
