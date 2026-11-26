package com.devcool.adapters.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.support.AbstractWsIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.CloseStatus;

/** Protocol v2 over a real socket (P4-T02, ADR-0005). */
class WsProtocolV2IT extends AbstractWsIT {

  /** Same spy as {@link WsVirtualThreadsIT}, so both share one cached context. */
  @MockitoSpyBean SendMessageUseCase sendMessageUseCase;

  @Test
  void send_acksWithTheReqIdAndIds_andTheListenerGetsMessageNew() throws Exception {
    Account sender = signUp("v2s");
    Account receiver = signUp("v2r");
    Long channelId = createLounge(sender, receiver);
    Client listening = connect(receiver);
    subscribe(listening, channelId);
    Client sending = connect(sender);
    String key = uuid();

    sendFrame(
        sending,
        v2(
            "SEND",
            channelId,
            "r-1",
            Map.of("clientMsgId", key, "contentType", "TEXT", "content", "hello v2")));

    JsonNode ack = json.readTree(awaitFrame(sending));
    assertThat(ack.path("v").asInt()).isEqualTo(2);
    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("reqId").asText()).isEqualTo("r-1");
    assertThat(ack.path("payload").path("clientMsgId").asText()).isEqualTo(key);
    long messageId = ack.path("payload").path("messageId").asLong();
    long seq = ack.path("payload").path("seq").asLong();
    assertThat(messageId).isPositive();
    assertThat(seq).isPositive();
    assertThat(ack.path("payload").path("createdTime").asText()).isNotBlank();

    JsonNode push = json.readTree(awaitFrame(listening));
    assertThat(push.path("v").asInt()).isEqualTo(2);
    assertThat(push.path("type").asText()).isEqualTo("MESSAGE_NEW");
    assertThat(push.path("channelId").asLong()).isEqualTo(channelId);
    assertThat(push.path("payload").path("messageId").asLong()).isEqualTo(messageId);
    assertThat(push.path("payload").path("seq").asLong()).isEqualTo(seq);
    assertThat(push.path("payload").path("senderId").asLong()).isEqualTo(sender.id());
    assertThat(push.path("payload").path("content").asText()).isEqualTo("hello v2");
    assertThat(push.path("payload").path("createdTime").asText()).isNotBlank();
  }

  @Test
  void subscribeByNonMember_nacksForbidden_andTheSocketStaysUsable() throws Exception {
    Account owner = signUp("v2o");
    Account member = signUp("v2m");
    Account outsider = signUp("v2x");
    Long foreign = createLounge(owner, member);
    Long own = createLounge(outsider, member);
    Client client = connect(outsider);

    sendFrame(client, v2("SUBSCRIBE", foreign, "r-2", null));

    JsonNode nack = json.readTree(awaitFrame(client));
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("reqId").asText()).isEqualTo("r-2");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("FORBIDDEN");
    subscribe(client, own);
    assertThat(client.session().isOpen()).isTrue();
  }

  @Test
  void unexpectedServerFailure_nacksInternal_andTheSocketStaysUsable() throws Exception {
    Account sender = signUp("v2f");
    Long channelId = createLounge(sender, signUp("v2g"));
    Client client = connect(sender);
    doThrow(new IllegalStateException("simulated failure"))
        .doCallRealMethod()
        .when(sendMessageUseCase)
        .sendMessage(any(), any());

    String failed = send(client, channelId, "first");

    JsonNode nack = json.readTree(awaitFrame(client));
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("reqId").asText()).isEqualTo(failed);
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INTERNAL");
    sendAndAwaitAck(client, channelId, "second");
  }

  @Test
  void unknownProtocolVersion_closesWith4400() throws Exception {
    Account account = signUp("v2v");
    Client client = connect(account);

    sendFrame(client, Map.of("v", 3, "type", "SUBSCRIBE", "reqId", "r-3", "channelId", 1));

    assertThat(client.closed())
        .succeedsWithin(TIMEOUT)
        .extracting(CloseStatus::getCode)
        .isEqualTo(4400);
  }
}
