package com.devcool.adapters.in.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.adapters.in.websocket.dto.WsMessageType;
import com.devcool.adapters.in.websocket.dto.WsServerFrame;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.WsSubscribeUseCase;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class RawWebSocketHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private WsSessionStore sessionStore;
  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @Mock private WsSubscribeUseCase subscribeUseCase;
  @Mock private SendMessageUseCase sendMessageUseCase;
  @Mock private WebSocketSession session;

  private RawWebSocketHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new RawWebSocketHandler(
            objectMapper,
            sessionStore,
            connectionRegistryPort,
            subscribeUseCase,
            sendMessageUseCase);
    when(session.getId()).thenReturn("conn-1");
    when(session.getAttributes()).thenReturn(new HashMap<>());
  }

  @Test
  void handleTextMessage_malformedJson_repliesWithInvalidJsonErrorAndCallsNoUseCase()
      throws Exception {
    handler.handleTextMessage(session, new TextMessage("{not json"));

    ArgumentCaptor<TextMessage> reply = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(reply.capture());
    WsServerFrame frame =
        objectMapper.readValue(reply.getValue().getPayload(), WsServerFrame.class);
    assertThat(frame.type()).isEqualTo(WsMessageType.ERROR);
    assertThat(frame.data()).isEqualTo("Invalid JSON");
    verifyNoInteractions(subscribeUseCase, sendMessageUseCase);
  }

  @Test
  void handleTextMessage_unknownAction_repliesWithInvalidJsonError() throws Exception {
    handler.handleTextMessage(session, new TextMessage("{\"action\":\"NOPE\"}"));

    ArgumentCaptor<TextMessage> reply = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(reply.capture());
    assertThat(reply.getValue().getPayload()).contains("Invalid JSON");
  }
}
