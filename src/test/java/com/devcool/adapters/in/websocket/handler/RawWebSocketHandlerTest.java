package com.devcool.adapters.in.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.adapters.in.websocket.dto.WsMessageType;
import com.devcool.adapters.in.websocket.dto.WsServerFrame;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.WsSubscribeUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
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

  // ---- SEND_MESSAGE: idempotency key (P3-T03) -------------------------------------------------

  private static final String KEY = "7f1c2a9e-0000-4000-8000-000000000001";

  @Test
  void send_passesTheParsedClientMsgIdToTheUseCase() throws Exception {
    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    ArgumentCaptor<SendMessageCommand> command = ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(sendMessageUseCase).sendMessage(command.capture());
    assertThat(command.getValue().clientMsgId()).isEqualTo(UUID.fromString(KEY));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\"clientMsgId\":null", "\"clientMsgId\":\"not-a-uuid\""})
  void sendWithMissingOrInvalidClientMsgId_repliesErrorAndSavesNothing(String keyField)
      throws Exception {
    handler.handleTextMessage(session, sendFrame(keyField));

    WsServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(WsMessageType.ERROR);
    assertThat(frame.data()).isEqualTo("Invalid clientMsgId");
    assertThat(frame.clientMsgId()).as("the raw client string is not reflected").isNull();
    verifyNoInteractions(sendMessageUseCase);
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void sendRejectedByTheDomain_repliesErrorWithItsCode_andKeepsTheSocketOpen() throws Exception {
    doThrow(new ClientMsgIdReusedException()).when(sendMessageUseCase).sendMessage(any());

    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    WsServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(WsMessageType.ERROR);
    assertThat(frame.clientMsgId()).isEqualTo(KEY);
    assertThat(frame.data()).isEqualTo("MESSAGE_410");
    verify(session, never()).close(any(CloseStatus.class));
  }

  /** A SEND_MESSAGE frame; {@code keyField} is the raw JSON for the key ("" leaves it out). */
  private static TextMessage sendFrame(String keyField) {
    String key = keyField.isEmpty() ? "" : "," + keyField;
    return new TextMessage(
        "{\"action\":\"SEND_MESSAGE\",\"channelId\":1,\"contentType\":\"TEXT\","
            + "\"content\":\"hi\""
            + key
            + "}");
  }

  private WsServerFrame onlyReply() throws Exception {
    ArgumentCaptor<TextMessage> reply = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(reply.capture());
    return objectMapper.readValue(reply.getValue().getPayload(), WsServerFrame.class);
  }
}
