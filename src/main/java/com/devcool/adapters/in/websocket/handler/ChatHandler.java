package com.devcool.adapters.in.websocket.handler;

import com.devcool.adapters.in.websocket.config.WsProtocolProps;
import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.v2.NackCode;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.handler.action.FrameAction;
import com.devcool.adapters.in.websocket.handler.v2.FrameDispatcher;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.adapters.in.websocket.session.ProtocolVersions;
import com.devcool.adapters.in.websocket.session.SessionRegistry;
import com.devcool.domain.chat.port.in.ConnectionLifecycleUseCase;
import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;
import com.devcool.domain.common.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);

  /** ADR-0005 rule 2: the client must reload for a protocol this server speaks. */
  static final CloseStatus PROTOCOL_MISMATCH =
      new CloseStatus(4400, "Unsupported protocol version");

  private final ObjectMapper objectMapper;
  private final SessionRegistry sessionRegistry;
  private final ConnectionLifecycleUseCase connections;
  private final FrameWriter writer;
  private final Map<FrameType, FrameAction> actions = new EnumMap<>(FrameType.class);
  private final FrameDispatcher dispatcher;
  private final WsProtocolProps protocol;

  public ChatHandler(
      ObjectMapper objectMapper,
      SessionRegistry sessionRegistry,
      ConnectionLifecycleUseCase connections,
      FrameWriter writer,
      List<FrameAction> actions,
      FrameDispatcher dispatcher,
      WsProtocolProps protocol) {
    this.objectMapper = objectMapper;
    this.sessionRegistry = sessionRegistry;
    this.connections = connections;
    this.writer = writer;
    this.dispatcher = dispatcher;
    this.protocol = protocol;
    for (FrameAction action : actions) {
      if (Objects.nonNull(this.actions.put(action.type(), action))) {
        throw new IllegalStateException("Two actions for " + action.type());
      }
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessionRegistry.register(session);
    connections.connect(
        new ConnectCommand(session.getId(), AuthHandshakeInterceptor.userIdOf(session)));
  }

  @Override
  public void handleTextMessage(WebSocketSession raw, TextMessage message) throws IOException {
    // Write only through the wrapper; none means already cleaned up.
    WebSocketSession session = sessionRegistry.get(raw.getId());
    if (Objects.isNull(session)) {
      log.debug("Frame on unregistered session {} dropped", raw.getId());
      return;
    }
    JsonNode tree = parse(message.getPayload());
    if (Objects.isNull(tree) || !tree.isObject()) {
      writer.send(session, ServerEnvelope.error(NackCode.INVALID, "Invalid JSON"));
      return;
    }
    JsonNode version = tree.get("v");
    if (Objects.isNull(version) || version.isNull() || isInt(version, 1)) {
      handleLegacy(session, tree);
    } else if (isInt(version, ServerEnvelope.VERSION)) {
      ProtocolVersions.mark(session, ServerEnvelope.VERSION);
      dispatcher.dispatch(session, tree);
    } else {
      session.close(PROTOCOL_MISMATCH);
    }
  }

  /** v1 frames (ADR-0005 rule 5): accepted behind the flag until P4-T17. */
  private void handleLegacy(WebSocketSession session, JsonNode tree) throws IOException {
    if (!protocol.legacyV1Enabled()) {
      session.close(PROTOCOL_MISMATCH);
      return;
    }
    ProtocolVersions.mark(session, 1);
    ClientFrame frame;
    try {
      frame = objectMapper.treeToValue(tree, ClientFrame.class);
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

  private JsonNode parse(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }

  private static boolean isInt(JsonNode node, int value) {
    return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == value;
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception)
      throws IOException {
    cleanUp(session.getId());
    if (session.isOpen()) {
      // Raw close on purpose: the transport is broken, so queued sends are moot.
      session.close(CloseStatus.SESSION_NOT_RELIABLE);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    cleanUp(session.getId());
  }

  /** Idempotent: runs on transport error, then again on close. */
  private void cleanUp(String connectionId) {
    sessionRegistry.remove(connectionId);
    connections.disconnect(new DisconnectCommand(connectionId));
  }

  /** Any exception becomes an ERROR frame: an escaping one makes Spring close the socket. */
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
    } catch (RuntimeException ex) {
      log.error("Unhandled {} frame failure", frame.action(), ex);
      writer.error(session, frame.clientMsgId(), "INTERNAL");
    }
  }
}
