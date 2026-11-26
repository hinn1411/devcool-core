package com.devcool.adapters.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.support.AbstractWsIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * P4-T04: EDIT/DELETE/REACT/READ over a real socket. The actor gets one ACK/NACK, the channel's
 * other subscribers one event, only after commit and only on a real change. "No event" is proven by
 * a PING whose PONG is the next frame: any event is written before the actor's ACK.
 */
class WsMessageEventsIT extends AbstractWsIT {

  /** Same spy as {@link WsProtocolV2IT}, so all three share one cached context. */
  @MockitoSpyBean SendMessageUseCase sendMessageUseCase;

  private Account alice;
  private Account bob;
  private Long channelId;
  private Client aliceWs;
  private Client bobWs;
  private long messageId;
  private long seq;

  /** Alice and Bob share a lounge, both subscribed; Alice has posted "draft". */
  @BeforeEach
  void seed() throws Exception {
    alice = signUp("eva");
    bob = signUp("evb");
    channelId = createLounge(alice, bob);
    aliceWs = connect(alice);
    subscribe(aliceWs, channelId);
    bobWs = connect(bob);
    subscribe(bobWs, channelId);
    JsonNode ack = json.readTree(awaitFrame(aliceWs, send(aliceWs, channelId, "draft")));
    messageId = ack.path("payload").path("messageId").asLong();
    seq = ack.path("payload").path("seq").asLong();
    assertThat(event(bobWs).path("type").asText()).isEqualTo("MESSAGE_NEW");
  }

  // ---- 1. EDIT ---------------------------------------------------------------------------------

  @Test
  void edit_acksTheNewVersion_andOtherSubscribersGetMessageUpdated_butNotTheActorsSocket()
      throws Exception {
    Client aliceOtherTab = connect(alice);
    subscribe(aliceOtherTab, channelId);

    JsonNode ack =
        request(
            aliceWs,
            "EDIT",
            Map.of("messageId", messageId, "content", "final", "expectedVersion", 0));

    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("payload").path("messageId").asLong()).isEqualTo(messageId);
    assertThat(ack.path("payload").path("version").asInt()).isEqualTo(1);
    assertThat(ack.path("payload").path("editedTime").asText()).isNotBlank();
    for (Client other : new Client[] {bobWs, aliceOtherTab}) {
      JsonNode updated = event(other);
      assertThat(updated.path("type").asText()).isEqualTo("MESSAGE_UPDATED");
      assertThat(updated.path("channelId").asLong()).isEqualTo(channelId);
      JsonNode payload = updated.path("payload");
      assertThat(payload.path("messageId").asLong()).isEqualTo(messageId);
      assertThat(payload.path("seq").asLong()).isEqualTo(seq);
      assertThat(payload.path("createdTime").asText()).isNotBlank();
      assertThat(payload.path("content").asText()).isEqualTo("final");
      assertThat(payload.path("version").asInt()).isEqualTo(1);
      assertThat(payload.path("editedTime").asText())
          .isEqualTo(ack.path("payload").path("editedTime").asText());
    }
    assertNoEvent(aliceWs);
  }

  // ---- 2. EDIT refused -------------------------------------------------------------------------

  @Test
  void staleEdit_nacksConflict_keepsTheSocket_andNobodyGetsAnEvent() throws Exception {
    JsonNode nack =
        request(
            aliceWs,
            "EDIT",
            Map.of("messageId", messageId, "content", "final", "expectedVersion", 5));

    assertNack(nack, "CONFLICT");
    assertNoEvent(bobWs);
    assertThat(aliceWs.session().isOpen()).isTrue();
  }

  @Test
  void editByAnotherMember_nacksForbidden_andNobodyGetsAnEvent() throws Exception {
    JsonNode nack =
        request(
            bobWs, "EDIT", Map.of("messageId", messageId, "content", "mine", "expectedVersion", 0));

    assertNack(nack, "FORBIDDEN");
    assertNoEvent(aliceWs);
  }

  // ---- 3. DELETE + 6. no-op --------------------------------------------------------------------

  @Test
  void delete_acks_andBroadcastsOneTombstone_aRepeatIsSilent() throws Exception {
    assertThat(request(aliceWs, "DELETE", Map.of("messageId", messageId)).path("type").asText())
        .isEqualTo("ACK");

    JsonNode deleted = event(bobWs);
    assertThat(deleted.path("type").asText()).isEqualTo("MESSAGE_DELETED");
    assertThat(deleted.path("channelId").asLong()).isEqualTo(channelId);
    assertThat(deleted.path("payload").path("messageId").asLong()).isEqualTo(messageId);
    assertThat(deleted.path("payload").path("seq").asLong()).isEqualTo(seq);
    assertThat(deleted.path("payload").path("createdTime").asText()).isNotBlank();

    assertThat(request(aliceWs, "DELETE", Map.of("messageId", messageId)).path("type").asText())
        .isEqualTo("ACK");
    assertNoEvent(bobWs);
  }

  // ---- 4. REACT + 6. no-op ---------------------------------------------------------------------

  @Test
  void react_onAndOff_broadcastsEachChange_aRepeatIsSilent() throws Exception {
    Map<String, Object> on = Map.of("messageId", messageId, "emoji", "👍", "on", true);

    assertThat(request(bobWs, "REACT", on).path("type").asText()).isEqualTo("ACK");
    JsonNode added = event(aliceWs);
    assertThat(added.path("type").asText()).isEqualTo("REACTION");
    assertThat(added.path("channelId").asLong()).isEqualTo(channelId);
    assertThat(added.path("payload").path("messageId").asLong()).isEqualTo(messageId);
    assertThat(added.path("payload").path("emoji").asText()).isEqualTo("👍");
    assertThat(added.path("payload").path("userId").asLong()).isEqualTo(bob.id());
    assertThat(added.path("payload").path("on").asBoolean()).isTrue();

    assertThat(request(bobWs, "REACT", on).path("type").asText()).isEqualTo("ACK");
    assertNoEvent(aliceWs);

    request(bobWs, "REACT", Map.of("messageId", messageId, "emoji", "👍", "on", false));
    JsonNode removed = event(aliceWs);
    assertThat(removed.path("type").asText()).isEqualTo("REACTION");
    assertThat(removed.path("payload").path("on").asBoolean()).isFalse();
  }

  // ---- 5. READ + 6. no-op ----------------------------------------------------------------------

  @Test
  void read_acksTheClampedMarker_andBroadcastsAReceipt_onlyWhenItMoves() throws Exception {
    JsonNode ack = request(bobWs, "READ", Map.of("seq", 999));

    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("payload").path("seq").asLong()).isEqualTo(seq);
    JsonNode receipt = event(aliceWs);
    assertThat(receipt.path("type").asText()).isEqualTo("READ_RECEIPT");
    assertThat(receipt.path("channelId").asLong()).isEqualTo(channelId);
    assertThat(receipt.path("payload").path("userId").asLong()).isEqualTo(bob.id());
    assertThat(receipt.path("payload").path("seq").asLong()).isEqualTo(seq);

    assertThat(request(bobWs, "READ", Map.of("seq", seq)).path("payload").path("seq").asLong())
        .isEqualTo(seq);
    assertNoEvent(aliceWs);
  }

  // ---- 7. REST publishes the same event --------------------------------------------------------

  @Test
  void restEdit_reachesEveryWsSubscriber_includingTheAuthorsOwnSockets() throws Exception {
    mvc.perform(
            patch("/api/v1/messages/{id}", messageId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"via rest\",\"version\":0}"))
        .andExpect(status().isOk());

    for (Client subscriber : new Client[] {aliceWs, bobWs}) {
      JsonNode updated = event(subscriber);
      assertThat(updated.path("type").asText()).isEqualTo("MESSAGE_UPDATED");
      assertThat(updated.path("payload").path("content").asText()).isEqualTo("via rest");
      assertThat(updated.path("payload").path("version").asInt()).isEqualTo(1);
    }
  }

  // ---- negative authz --------------------------------------------------------------------------

  @Test
  void nonMember_allFourFrames_nackNotFound_andSubscribersGetNothing() throws Exception {
    Client outsider = connect(signUp("evx"));

    assertNack(
        request(
            outsider, "EDIT", Map.of("messageId", messageId, "content", "x", "expectedVersion", 0)),
        "NOT_FOUND");
    assertNack(request(outsider, "DELETE", Map.of("messageId", messageId)), "NOT_FOUND");
    assertNack(
        request(outsider, "REACT", Map.of("messageId", messageId, "emoji", "👍", "on", true)),
        "NOT_FOUND");
    assertNack(request(outsider, "READ", Map.of("seq", seq)), "NOT_FOUND");
    assertNoEvent(aliceWs);
    assertNoEvent(bobWs);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  /** Sends one frame on {@link #channelId} and returns the reply carrying its reqId. */
  private JsonNode request(Client client, String type, Map<String, Object> payload)
      throws Exception {
    String reqId = uuid();
    sendFrame(client, v2(type, channelId, reqId, payload));
    return json.readTree(awaitFrame(client, reqId));
  }

  /** The next frame with this reqId; fails on anything else in between. */
  private String awaitFrame(Client client, String reqId) throws Exception {
    String frame = awaitFrame(client);
    assertThat(json.readTree(frame).path("reqId").asText()).as(frame).isEqualTo(reqId);
    return frame;
  }

  private JsonNode event(Client client) throws Exception {
    JsonNode frame = json.readTree(awaitFrame(client));
    assertThat(frame.hasNonNull("reqId")).as("an event, not a reply: %s", frame).isFalse();
    return frame;
  }

  private void assertNoEvent(Client client) throws Exception {
    String reqId = uuid();
    sendFrame(client, v2("PING", null, reqId, null));
    JsonNode next = json.readTree(awaitFrame(client));
    assertThat(next.path("type").asText()).as("expected only PONG, got %s", next).isEqualTo("PONG");
    assertThat(next.path("reqId").asText()).isEqualTo(reqId);
  }

  private static void assertNack(JsonNode reply, String code) {
    assertThat(reply.path("type").asText()).as(reply.toString()).isEqualTo("NACK");
    assertThat(reply.path("payload").path("code").asText()).isEqualTo(code);
  }
}
