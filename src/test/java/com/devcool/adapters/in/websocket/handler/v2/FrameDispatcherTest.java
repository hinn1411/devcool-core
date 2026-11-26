package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.model.ClientMsgId;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.ChannelSubscriptionUseCase;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.common.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
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

/** v2 frames (P4-T02): one ACK/NACK per reqId, never a close. */
@ExtendWith(MockitoExtension.class)
class FrameDispatcherTest {

  private static final String KEY = "7f1c2a9e-0000-4000-8000-000000000001";
  private static final SavedMessage SAVED =
      new SavedMessage(100L, 42L, Instant.parse("2026-11-01T10:00:00Z"), false);

  // As Boot's: ISO-8601 dates, unknown properties ignored.
  private final ObjectMapper objectMapper =
      Jackson2ObjectMapperBuilder.json()
          .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  @Mock private ChannelSubscriptionUseCase subscriptions;
  @Mock private SendMessageUseCase sendMessageUseCase;
  @Mock private WebSocketSession session;

  private FrameDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    FrameWriter writer = new FrameWriter(objectMapper);
    dispatcher =
        new FrameDispatcher(
            objectMapper,
            writer,
            List.of(
                new SubscribeHandler(writer, subscriptions),
                new UnsubscribeHandler(writer, subscriptions),
                new SendHandler(writer, sendMessageUseCase)));
    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put(AuthHandshakeInterceptor.ATTR_USER_ID, 7L);
    lenient().when(session.getId()).thenReturn("conn-1");
    lenient().when(session.getAttributes()).thenReturn(attributes);
  }

  // ---- 1. one ACK per reqId ----

  @Test
  void subscribe_callsTheUseCaseAsTheSessionUser_andAcksWithTheReqId() throws Exception {
    dispatch("{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-1\",\"channelId\":42}");

    verify(subscriptions).subscribe(new SubscribeCommand("conn-1", 7L, 42L));
    JsonNode reply = onlyReply();
    assertThat(reply.path("v").asInt()).isEqualTo(2);
    assertThat(reply.path("type").asText()).isEqualTo("ACK");
    assertThat(reply.path("reqId").asText()).isEqualTo("r-1");
  }

  @Test
  void unsubscribe_callsTheUseCaseForThisConnection_andAcksWithTheReqId() throws Exception {
    dispatch("{\"v\":2,\"type\":\"UNSUBSCRIBE\",\"reqId\":\"r-2\",\"channelId\":42}");

    verify(subscriptions).unsubscribe(new UnsubscribeCommand("conn-1", 42L));
    JsonNode reply = onlyReply();
    assertThat(reply.path("type").asText()).isEqualTo("ACK");
    assertThat(reply.path("reqId").asText()).isEqualTo("r-2");
  }

  @Test
  void send_passesThePayloadToTheUseCase() throws Exception {
    commitsAs(SAVED);

    dispatch(sendFrame("r-3", "\"clientMsgId\":\"" + KEY + "\",\"replyToId\":77"));

    ArgumentCaptor<SendMessageCommand> command = ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(sendMessageUseCase).sendMessage(command.capture(), any());
    assertThat(command.getValue())
        .isEqualTo(
            new SendMessageCommand(
                "conn-1",
                7L,
                1L,
                ContentType.TEXT,
                "hi",
                new ClientMsgId(UUID.fromString(KEY)),
                77L));
  }

  @Test
  void send_acksWithClientMsgIdMessageIdSeqAndIsoCreatedTime() throws Exception {
    commitsAs(SAVED);

    dispatch(sendFrame("r-3", "\"clientMsgId\":\"" + KEY + "\""));

    JsonNode reply = onlyReply();
    assertThat(reply.path("type").asText()).isEqualTo("ACK");
    assertThat(reply.path("reqId").asText()).isEqualTo("r-3");
    JsonNode payload = reply.path("payload");
    assertThat(payload.path("clientMsgId").asText()).isEqualTo(KEY);
    assertThat(payload.path("messageId").asLong()).isEqualTo(100L);
    assertThat(payload.path("seq").asLong()).isEqualTo(42L);
    assertThat(payload.path("createdTime").asText()).isEqualTo("2026-11-01T10:00:00Z");
  }

  @Test
  void send_repliesNothingUntilTheUseCaseCommits() throws Exception {
    // Commit → ACK → publish: the ACK is written from the commit callback only.
    dispatch(sendFrame("r-3", "\"clientMsgId\":\"" + KEY + "\""));

    verify(sendMessageUseCase).sendMessage(any(), any());
    verify(session, never()).sendMessage(any());
  }

  // ---- 2. unknown types and fields ----

  @ParameterizedTest
  @ValueSource(strings = {"EDIT", "TYPING", "RESUME", "NOT_A_TYPE"})
  void unknownOrNotYetBuiltType_nacksUnsupported(String type) throws Exception {
    dispatch("{\"v\":2,\"type\":\"" + type + "\",\"reqId\":\"r-4\",\"channelId\":42}");

    assertNack(onlyReply(), "r-4", "UNSUPPORTED");
    verifyNoInteractions(subscriptions, sendMessageUseCase);
  }

  @Test
  void unknownFields_areIgnored() throws Exception {
    dispatch(
        "{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-5\",\"channelId\":42,"
            + "\"future\":true,\"payload\":{\"alsoFuture\":1}}");

    verify(subscriptions).subscribe(new SubscribeCommand("conn-1", 7L, 42L));
    assertThat(onlyReply().path("type").asText()).isEqualTo("ACK");
  }

  @Test
  void missingType_nacksInvalid() throws Exception {
    dispatch("{\"v\":2,\"reqId\":\"r-6\",\"channelId\":42}");

    assertNack(onlyReply(), "r-6", "INVALID");
  }

  // ---- 3. bad payload ----

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-7\"}",
        "{\"v\":2,\"type\":\"UNSUBSCRIBE\",\"reqId\":\"r-7\"}",
        "{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-7\",\"channelId\":\"abc\"}",
        "{\"v\":2,\"type\":\"SEND\",\"reqId\":\"r-7\",\"channelId\":1}",
        "{\"v\":2,\"type\":\"SEND\",\"reqId\":\"r-7\",\"channelId\":1,"
            + "\"payload\":{\"clientMsgId\":\"not-a-uuid\",\"contentType\":\"TEXT\"}}",
        "{\"v\":2,\"type\":\"SEND\",\"reqId\":\"r-7\",\"channelId\":1,"
            + "\"payload\":{\"clientMsgId\":\""
            + KEY
            + "\",\"contentType\":\"NOT_A_TYPE\"}}",
        "{\"v\":2,\"type\":\"SEND\",\"reqId\":\"r-7\",\"channelId\":1,\"payload\":\"text\"}"
      })
  void badPayload_nacksInvalidWithTheReqId_andCallsNoUseCase(String frame) throws Exception {
    dispatch(frame);

    assertNack(onlyReply(), "r-7", "INVALID");
    verifyNoInteractions(subscriptions, sendMessageUseCase);
  }

  // ---- 4. reqId required ----

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"v\":2,\"type\":\"SUBSCRIBE\",\"channelId\":42}",
        "{\"v\":2,\"type\":\"UNSUBSCRIBE\",\"reqId\":\"\",\"channelId\":42}",
        "{\"v\":2,\"type\":\"SEND\",\"reqId\":null,\"channelId\":1,"
            + "\"payload\":{\"clientMsgId\":\""
            + KEY
            + "\",\"contentType\":\"TEXT\",\"content\":\"hi\"}}"
      })
  void frameWithoutReqId_nacksInvalidWithoutReqId_andDoesNotRun(String frame) throws Exception {
    dispatch(frame);

    JsonNode reply = onlyReply();
    assertThat(reply.path("type").asText()).isEqualTo("NACK");
    assertThat(reply.path("payload").path("code").asText()).isEqualTo("INVALID");
    assertThat(reply.hasNonNull("reqId")).isFalse();
    verifyNoInteractions(subscriptions, sendMessageUseCase);
  }

  // ---- 5. domain errors → mapped NACK codes ----

  @Test
  void subscribeByNonMember_nacksForbiddenWithTheDomainCode() throws Exception {
    doThrow(new ForbiddenException("Not a member"))
        .when(subscriptions)
        .subscribe(any(SubscribeCommand.class));

    dispatch("{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-8\",\"channelId\":42}");

    JsonNode reply = onlyReply();
    assertNack(reply, "r-8", "FORBIDDEN");
    assertThat(reply.path("payload").path("message").asText()).isEqualTo("SVR_403");
  }

  @Test
  void sendWithAReusedKey_nacksConflictWithTheDomainCode() throws Exception {
    doThrow(new ClientMsgIdReusedException()).when(sendMessageUseCase).sendMessage(any(), any());

    dispatch(sendFrame("r-9", "\"clientMsgId\":\"" + KEY + "\""));

    JsonNode reply = onlyReply();
    assertNack(reply, "r-9", "CONFLICT");
    assertThat(reply.path("payload").path("message").asText()).isEqualTo("MESSAGE_410");
  }

  // ---- 6. anything else → INTERNAL, socket stays usable ----

  @Test
  void unexpectedFailure_nacksInternal_andTheNextFrameStillWorks() throws Exception {
    doThrow(new IllegalStateException("db timeout"))
        .doNothing()
        .when(subscriptions)
        .subscribe(any(SubscribeCommand.class));

    dispatch("{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-10\",\"channelId\":42}");
    dispatch("{\"v\":2,\"type\":\"SUBSCRIBE\",\"reqId\":\"r-11\",\"channelId\":42}");

    List<JsonNode> replies = replies(2);
    JsonNode internal = replies.get(0);
    assertNack(internal, "r-10", "INTERNAL");
    assertThat(internal.path("payload").path("message").asText())
        .as("no exception detail leaks to the client")
        .doesNotContain("db timeout");
    assertThat(replies.get(1).path("type").asText()).isEqualTo("ACK");
    assertThat(replies.get(1).path("reqId").asText()).isEqualTo("r-11");
  }

  @Test
  void failedAckWriteAfterCommit_nacksInternal_soTheClientRetriesTheSameKey() throws Exception {
    doAnswer(
            call -> {
              throw new UncheckedIOException(new IOException("ack write failed"));
            })
        .when(sendMessageUseCase)
        .sendMessage(any(), any());

    dispatch(sendFrame("r-12", "\"clientMsgId\":\"" + KEY + "\""));

    assertNack(onlyReply(), "r-12", "INTERNAL");
  }

  // ---- fixtures ----

  private void dispatch(String json) throws Exception {
    dispatcher.dispatch(session, objectMapper.readTree(json));
  }

  private static String sendFrame(String reqId, String keyField) {
    return "{\"v\":2,\"type\":\"SEND\",\"reqId\":\""
        + reqId
        + "\",\"channelId\":1,\"payload\":{\"contentType\":\"TEXT\",\"content\":\"hi\","
        + keyField
        + "}}";
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

  private static void assertNack(JsonNode reply, String reqId, String code) {
    assertThat(reply.path("v").asInt()).isEqualTo(2);
    assertThat(reply.path("type").asText()).isEqualTo("NACK");
    assertThat(reply.path("reqId").asText()).isEqualTo(reqId);
    assertThat(reply.path("payload").path("code").asText()).isEqualTo(code);
  }

  private JsonNode onlyReply() throws Exception {
    return replies(1).get(0);
  }

  /** Also asserts the session was never closed. */
  private List<JsonNode> replies(int count) throws Exception {
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, times(count)).sendMessage(sent.capture());
    verify(session, never()).close(any(CloseStatus.class));
    return sent.getAllValues().stream().map(this::parse).toList();
  }

  private JsonNode parse(TextMessage message) {
    try {
      return objectMapper.readTree(message.getPayload());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
