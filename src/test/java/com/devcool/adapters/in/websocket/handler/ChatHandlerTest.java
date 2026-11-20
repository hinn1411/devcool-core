package com.devcool.adapters.in.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.ServerFrame;
import com.devcool.adapters.in.websocket.handler.action.PingAction;
import com.devcool.adapters.in.websocket.handler.action.SendMessageAction;
import com.devcool.adapters.in.websocket.handler.action.SubscribeAction;
import com.devcool.adapters.in.websocket.handler.action.UnsubscribeAction;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.adapters.in.websocket.session.SessionRegistry;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.model.ClientMsgId;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.WsSubscribeUseCase;
import com.devcool.domain.chat.port.in.WsUnsubscribeUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.common.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class ChatHandlerTest {

  // As Boot's: ISO-8601 dates.
  private final ObjectMapper objectMapper =
      Jackson2ObjectMapperBuilder.json()
          .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private static final SavedMessage SAVED =
      new SavedMessage(100L, 42L, Instant.parse("2026-11-01T10:00:00Z"), false);

  @Mock private SessionRegistry sessionRegistry;
  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @Mock private WsSubscribeUseCase subscribeUseCase;
  @Mock private WsUnsubscribeUseCase unsubscribeUseCase;
  @Mock private SendMessageUseCase sendMessageUseCase;
  @Mock private WebSocketSession session;

  private ChatHandler handler;

  @BeforeEach
  void setUp() {
    FrameWriter writer = new FrameWriter(objectMapper);
    handler =
        new ChatHandler(
            objectMapper,
            sessionRegistry,
            connectionRegistryPort,
            writer,
            List.of(
                new PingAction(writer),
                new SubscribeAction(writer, subscribeUseCase),
                new UnsubscribeAction(writer, unsubscribeUseCase),
                new SendMessageAction(writer, sendMessageUseCase)));
    // Lenient: only the actions that need them read the id and user.
    lenient().when(session.getId()).thenReturn("conn-1");
    lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
  }

  @Test
  void handleTextMessage_malformedJson_repliesWithInvalidJsonErrorAndCallsNoUseCase()
      throws Exception {
    handler.handleTextMessage(session, new TextMessage("{not json"));

    ArgumentCaptor<TextMessage> reply = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(reply.capture());
    ServerFrame frame = objectMapper.readValue(reply.getValue().getPayload(), ServerFrame.class);
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
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

  @Test
  void clientSendsAnAckFrame_repliesUnsupportedAction() throws Exception {
    handler.handleTextMessage(
        session, new TextMessage("{\"action\":\"ACK\",\"clientMsgId\":\"r-0\"}"));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.data()).isEqualTo("Unsupported action");
  }

  // ---- SUBSCRIBE
  // ---------------------------------------------------------------------------------

  @Test
  void subscribeByNonMember_repliesErrorWithItsCode_andKeepsTheSocketOpen() throws Exception {
    doThrow(new ForbiddenException("Not a member"))
        .when(subscribeUseCase)
        .subscribe(any(SubscribeCommand.class));

    handler.handleTextMessage(
        session,
        new TextMessage("{\"action\":\"SUBSCRIBE\",\"channelId\":42,\"clientMsgId\":\"r-3\"}"));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.clientMsgId()).isEqualTo("r-3");
    assertThat(frame.data()).isEqualTo(new ForbiddenException("x").getErrorCode().code());
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void subscribeWithoutChannelId_repliesErrorAndCallsNoUseCase() throws Exception {
    handler.handleTextMessage(
        session, new TextMessage("{\"action\":\"SUBSCRIBE\",\"clientMsgId\":\"r-4\"}"));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.data()).isEqualTo("Missing channelId");
    verifyNoInteractions(subscribeUseCase);
  }

  // ---- UNSUBSCRIBE (P3-T16) --------------------------------------------------------------------

  @Test
  void unsubscribe_repliesAckWithClientMsgId() throws Exception {
    handler.handleTextMessage(
        session,
        new TextMessage("{\"action\":\"UNSUBSCRIBE\",\"channelId\":42,\"clientMsgId\":\"r-1\"}"));

    verify(unsubscribeUseCase).unsubscribe(new UnsubscribeCommand("conn-1", 42L));
    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ACK);
    assertThat(frame.clientMsgId()).isEqualTo("r-1");
  }

  @Test
  void unsubscribe_usesSessionIdNotFrame() throws Exception {
    handler.handleTextMessage(
        session,
        new TextMessage(
            "{\"action\":\"UNSUBSCRIBE\",\"channelId\":42,\"connectionId\":\"someone-else\"}"));

    ArgumentCaptor<UnsubscribeCommand> command = ArgumentCaptor.forClass(UnsubscribeCommand.class);
    verify(unsubscribeUseCase).unsubscribe(command.capture());
    assertThat(command.getValue().connectionId()).isEqualTo("conn-1");
  }

  @Test
  void unsubscribeWithoutChannelId_repliesErrorAndCallsNoUseCase() throws Exception {
    handler.handleTextMessage(
        session, new TextMessage("{\"action\":\"UNSUBSCRIBE\",\"clientMsgId\":\"r-2\"}"));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.data()).isEqualTo("Missing channelId");
    verifyNoInteractions(unsubscribeUseCase);
    verify(session, never()).close(any(CloseStatus.class));
  }

  // ---- SEND_MESSAGE: idempotency key (P3-T03) -------------------------------------------------

  private static final String KEY = "7f1c2a9e-0000-4000-8000-000000000001";

  @Test
  void send_passesTheParsedClientMsgIdToTheUseCase() throws Exception {
    when(sendMessageUseCase.sendMessage(any())).thenReturn(SAVED);
    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    ArgumentCaptor<SendMessageCommand> command = ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(sendMessageUseCase).sendMessage(command.capture());
    assertThat(command.getValue().clientMsgId()).isEqualTo(new ClientMsgId(UUID.fromString(KEY)));
  }

  @Test
  void send_passesReplyToIdToTheUseCase() throws Exception {
    when(sendMessageUseCase.sendMessage(any())).thenReturn(SAVED);
    handler.handleTextMessage(
        session, sendFrame("\"clientMsgId\":\"" + KEY + "\",\"replyToId\":77"));

    ArgumentCaptor<SendMessageCommand> command = ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(sendMessageUseCase).sendMessage(command.capture());
    assertThat(command.getValue().replyToId()).isEqualTo(77L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\"clientMsgId\":null", "\"clientMsgId\":\"not-a-uuid\""})
  void sendWithMissingOrInvalidClientMsgId_repliesErrorAndSavesNothing(String keyField)
      throws Exception {
    handler.handleTextMessage(session, sendFrame(keyField));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.data()).isEqualTo("Invalid clientMsgId");
    assertThat(frame.clientMsgId()).as("the raw client string is not reflected").isNull();
    verifyNoInteractions(sendMessageUseCase);
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void sendRejectedByTheDomain_repliesErrorWithItsCode_andKeepsTheSocketOpen() throws Exception {
    doThrow(new ClientMsgIdReusedException()).when(sendMessageUseCase).sendMessage(any());

    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.clientMsgId()).isEqualTo(KEY);
    assertThat(frame.data()).isEqualTo("MESSAGE_410");
    verify(session, never()).close(any(CloseStatus.class));
  }

  // ---- SEND_MESSAGE: ACK ids (P3-T04) ---------------------------------------------------------

  @Test
  void send_ackCarriesMessageIdSeqAndIsoCreatedTime() throws Exception {
    when(sendMessageUseCase.sendMessage(any())).thenReturn(SAVED);

    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    assertAckCarriesSavedIds();
  }

  @Test
  void retriedSend_ackCarriesTheOriginalIds() throws Exception {
    when(sendMessageUseCase.sendMessage(any()))
        .thenReturn(new SavedMessage(100L, 42L, SAVED.createdTime(), true));

    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    assertAckCarriesSavedIds();
  }

  private void assertAckCarriesSavedIds() throws Exception {
    ArgumentCaptor<TextMessage> reply = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(reply.capture());
    JsonNode frame = objectMapper.readTree(reply.getValue().getPayload());
    assertThat(frame.path("type").asText()).isEqualTo("ACK");
    assertThat(frame.path("clientMsgId").asText()).isEqualTo(KEY);
    JsonNode data = frame.path("data");
    assertThat(data.path("messageId").asLong()).isEqualTo(100L);
    assertThat(data.path("seq").asLong()).isEqualTo(42L);
    assertThat(data.path("createdTime").asText()).isEqualTo("2026-11-01T10:00:00Z");
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

  private ServerFrame onlyReply() throws Exception {
    ArgumentCaptor<TextMessage> reply = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(reply.capture());
    return objectMapper.readValue(reply.getValue().getPayload(), ServerFrame.class);
  }
}
