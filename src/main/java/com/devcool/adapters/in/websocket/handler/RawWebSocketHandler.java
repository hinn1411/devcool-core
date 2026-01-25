package com.devcool.adapters.in.websocket.handler;

import com.devcool.adapters.in.websocket.dto.WsClientAction;
import com.devcool.adapters.in.websocket.dto.WsClientFrame;
import com.devcool.adapters.in.websocket.dto.WsServerFrame;
import com.devcool.adapters.in.websocket.security.WsAuthHandShakeInterceptor;
import com.devcool.domain.realtime.port.in.WsSubscribeUseCase;
import com.devcool.domain.realtime.port.in.command.SubscribeCommand;
import com.devcool.domain.realtime.port.out.ConnectionRegistryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RawWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper;

  private final WsSessionStore sessionStore;
  private final ConnectionRegistryPort connectionRegistryPort;
  private final WsSubscribeUseCase subscribeUseCase;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessionStore.put(session);
    Integer userId = getUserId(session);
    String connectionId = session.getId();
    connectionRegistryPort.registerConnection(connectionId, userId);

  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    String connectionId = session.getId();
    Integer userId = getUserId(session);

    WsClientFrame clientFrame;
    try {
      clientFrame = objectMapper.readValue(message.getPayload(), WsClientFrame.class);
    } catch (Exception ex) {
      session.sendMessage(new TextMessage(
          objectMapper.writeValueAsString(
              new WsServerFrame("ERROR", null, "Invalid JSON"))));
      return;
    }

    if (Objects.isNull(clientFrame.action())) {
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
          new WsServerFrame("ERROR", clientFrame.clientMsgId(), "Missing action")
      )));
      return;
    }

    if (clientFrame.action() == WsClientAction.PING) {
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
          new WsServerFrame("ACK", clientFrame.clientMsgId(), "PONG")
      )));
      return;
    }

    switch (clientFrame.action()) {
      case WsClientAction.SUBSCRIBE -> {
        subscribeUseCase.subscribe(new SubscribeCommand(connectionId, userId, clientFrame.channelId()));
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
            new WsServerFrame("ACK", clientFrame.clientMsgId(), Map.of("description", "Subscribe successfully"))
        )));
      }
      default -> {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
            new WsServerFrame("ERROR", clientFrame.clientMsgId(), "Unsupported action")
        )));
      }
    }


  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String connectionId = session.getId();
    sessionStore.remove(connectionId);
    connectionRegistryPort.removeConnection(connectionId);
  }

  private Integer getUserId(WebSocketSession session) {
    return (Integer) session.getAttributes().get(WsAuthHandShakeInterceptor.ATTR_USER_ID);
  }

}
