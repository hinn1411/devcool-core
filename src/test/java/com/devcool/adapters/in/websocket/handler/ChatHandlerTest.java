package com.devcool.adapters.in.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.adapters.in.websocket.session.SessionRegistry;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.model.ClientMsgId;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.ChannelSubscriptionUseCase;
import com.devcool.domain.chat.port.in.ConnectionLifecycleUseCase;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.common.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
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
  @Mock private ConnectionLifecycleUseCase connections;
  @Mock private ChannelSubscriptionUseCase subscriptions;
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
            connections,
            writer,
            List.of(
                new PingAction(writer),
                new SubscribeAction(writer, subscriptions),
                new UnsubscribeAction(writer, subscriptions),
                new SendMessageAction(writer, sendMessageUseCase)));
    lenient().when(session.getId()).thenReturn("conn-1");
    lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
    // Frames are answered through the registered session (P4-T01); here that's the mock itself.
    lenient().when(sessionRegistry.get("conn-1")).thenReturn(session);
  }

  // ---- Session lifecycle (P4-T01) ---------------------------------------------------------------

  @Test
  void connect_registersTheSession() {
    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put(AuthHandshakeInterceptor.ATTR_USER_ID, 7L);
    when(session.getAttributes()).thenReturn(attributes);

    handler.afterConnectionEstablished(session);

    verify(sessionRegistry).register(session);
    verify(connections).connect(new ConnectCommand("conn-1", 7L));
  }

  @Test
  void frames_areAnsweredThroughTheRegisteredWrapper_notTheRawSession() throws Exception {
    WebSocketSession wrapped = mock(WebSocketSession.class);
    lenient().when(wrapped.getId()).thenReturn("conn-1");
    lenient().when(wrapped.getAttributes()).thenReturn(new HashMap<>());
    when(sessionRegistry.get("conn-1")).thenReturn(wrapped);

    handler.handleTextMessage(
        session, new TextMessage("{\"action\":\"PING\",\"clientMsgId\":\"r-9\"}"));

    verify(wrapped).sendMessage(any(TextMessage.class));
    verify(session, never()).sendMessage(any());
  }

  @Test
  void transportError_cleansUpAndCloses_andTheLaterCloseCallbackIsANoOp() throws Exception {
    lenient().when(session.isOpen()).thenReturn(true);

    handler.handleTransportError(session, new IOException("connection reset"));

    verify(sessionRegistry).remove("conn-1");
    verify(connections).disconnect(new DisconnectCommand("conn-1"));
    verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
    assertThatCode(() -> handler.afterConnectionClosed(session, CloseStatus.SESSION_NOT_RELIABLE))
        .doesNotThrowAnyException();
  }

  // ---- SUBSCRIBE ----

  @Test
  void subscribeByNonMember_repliesErrorWithItsCode_andKeepsTheSocketOpen() throws Exception {
    doThrow(new ForbiddenException("Not a member"))
        .when(subscriptions)
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
    verifyNoInteractions(subscriptions);
  }

  // ---- UNSUBSCRIBE (P3-T16) --------------------------------------------------------------------

  @Test
  void unsubscribe_repliesAckWithClientMsgId() throws Exception {
    handler.handleTextMessage(
        session,
        new TextMessage("{\"action\":\"UNSUBSCRIBE\",\"channelId\":42,\"clientMsgId\":\"r-1\"}"));

    verify(subscriptions).unsubscribe(new UnsubscribeCommand("conn-1", 42L));
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
    verify(subscriptions).unsubscribe(command.capture());
    assertThat(command.getValue().connectionId()).isEqualTo("conn-1");
  }

  @Test
  void unsubscribeWithoutChannelId_repliesErrorAndCallsNoUseCase() throws Exception {
    handler.handleTextMessage(
        session, new TextMessage("{\"action\":\"UNSUBSCRIBE\",\"clientMsgId\":\"r-2\"}"));

    ServerFrame frame = onlyReply();
    assertThat(frame.type()).isEqualTo(FrameType.ERROR);
    assertThat(frame.data()).isEqualTo("Missing channelId");
    verifyNoInteractions(subscriptions);
    verify(session, never()).close(any(CloseStatus.class));
  }

  // ---- SEND_MESSAGE: idempotency key (P3-T03) -------------------------------------------------

  private static final String KEY = "7f1c2a9e-0000-4000-8000-000000000001";

  @Test
  void send_passesTheParsedClientMsgIdToTheUseCase() throws Exception {
    commitsAs(SAVED);
    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    ArgumentCaptor<SendMessageCommand> command = ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(sendMessageUseCase).sendMessage(command.capture(), any());
    assertThat(command.getValue().clientMsgId()).isEqualTo(new ClientMsgId(UUID.fromString(KEY)));
  }

  @Test
  void send_passesReplyToIdToTheUseCase() throws Exception {
    commitsAs(SAVED);
    handler.handleTextMessage(
        session, sendFrame("\"clientMsgId\":\"" + KEY + "\",\"replyToId\":77"));

    ArgumentCaptor<SendMessageCommand> command = ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(sendMessageUseCase).sendMessage(command.capture(), any());
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
    doThrow(new ClientMsgIdReusedException()).when(sendMessageUseCase).sendMessage(any(), any());

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
    commitsAs(SAVED);

    handler.handleTextMessage(session, sendFrame("\"clientMsgId\":\"" + KEY + "\""));

    assertAckCarriesSavedIds();
  }

  @Test
  void retriedSend_ackCarriesTheOriginalIds() throws Exception {
    commitsAs(new SavedMessage(100L, 42L, SAVED.createdTime(), true));

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

  @SuppressWarnings("unchecked")
  private void commitsAs(SavedMessage saved) {
    doAnswer(
            call -> {
              call.getArgument(1, Consumer.class).accept(saved);
              return null;
            })
        .when(sendMessageUseCase)
        .sendMessage(any(), any());
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
