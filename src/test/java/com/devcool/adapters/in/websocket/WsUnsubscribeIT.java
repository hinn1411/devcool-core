package com.devcool.adapters.in.websocket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.in.websocket.dto.WsClientFrame;
import com.devcool.adapters.in.websocket.dto.WsMessageType;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.user.port.out.UserPort;
import com.devcool.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * UNSUBSCRIBE and remove/leave cut off live pushes (P3-T16), over a real socket. "No push" is
 * proven without sleeping: after the cut-off message, a sentinel is sent on a channel the listener
 * still follows, and the listener's next frame must be that sentinel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
class WsUnsubscribeIT {

  private static final String PASSWORD = "Secret#123";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;

  private final List<WebSocketSession> openSessions = new ArrayList<>();

  record Account(Long id, String accessToken) {}

  record Client(WebSocketSession session, BlockingQueue<String> inbox) {}

  @AfterEach
  void closeSessions() throws Exception {
    for (WebSocketSession session : openSessions) {
      if (session.isOpen()) {
        session.close();
      }
    }
  }

  @Test
  void unsubscribedConnectionStopsReceivingPushes_andKeepsItsOtherChannels() throws Exception {
    Account sender = signUp("us");
    Account listener = signUp("ul");
    Long left = createLounge(sender, listener);
    Long kept = createLounge(sender, listener);
    Client listening = connect(listener);
    subscribe(listening, left);
    subscribe(listening, kept);
    Client sending = connect(sender);

    unsubscribe(listening, left);
    sendAndAwaitAck(sending, left, "after-unsubscribe");
    sendAndAwaitAck(sending, kept, "sentinel");

    assertThat(awaitFrame(listening)).contains("sentinel").doesNotContain("after-unsubscribe");
    sendAndAwaitAck(listening, kept, "listener can still send");
  }

  @Test
  void removedMemberStopsReceivingPushesOnEveryTab() throws Exception {
    Account owner = signUp("ro");
    Account bob = signUp("rb");
    Long removedFrom = createLounge(owner, bob);
    Long kept = createLounge(owner, bob);
    List<Client> tabs = List.of(connect(bob), connect(bob));
    for (Client tab : tabs) {
      subscribe(tab, removedFrom);
      subscribe(tab, kept);
    }
    Client sending = connect(owner);

    mvc.perform(
            delete("/api/v1/channels/{c}/members/{u}", removedFrom, bob.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken()))
        .andExpect(status().isNoContent());
    sendAndAwaitAck(sending, removedFrom, "after-remove");
    sendAndAwaitAck(sending, kept, "sentinel");

    for (Client tab : tabs) {
      assertThat(awaitFrame(tab)).contains("sentinel").doesNotContain("after-remove");
    }
  }

  @Test
  void memberWhoLeftStopsReceivingPushes() throws Exception {
    Account owner = signUp("lo");
    Account bob = signUp("lb");
    Long leftChannel = createLounge(owner, bob);
    Long kept = createLounge(owner, bob);
    Client tab = connect(bob);
    subscribe(tab, leftChannel);
    subscribe(tab, kept);
    Client sending = connect(owner);

    mvc.perform(
            delete("/api/v1/channels/{c}/members/me", leftChannel)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.accessToken()))
        .andExpect(status().isNoContent());
    sendAndAwaitAck(sending, leftChannel, "after-leave");
    sendAndAwaitAck(sending, kept, "sentinel");

    assertThat(awaitFrame(tab)).contains("sentinel").doesNotContain("after-leave");
  }

  // --- WS client ---

  private Client connect(Account account) throws Exception {
    BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    var headers = new WebSocketHttpHeaders();
    headers.setBearerAuth(account.accessToken());
    WebSocketSession session =
        new StandardWebSocketClient()
            .execute(
                new TextWebSocketHandler() {
                  @Override
                  protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                    inbox.add(message.getPayload());
                  }
                },
                headers,
                URI.create("ws://localhost:" + port + "/ws"))
            .get(TIMEOUT.toSeconds(), SECONDS);
    openSessions.add(session);
    return new Client(session, inbox);
  }

  private void subscribe(Client client, Long channelId) throws Exception {
    sendFrame(client, new WsClientFrame(WsMessageType.SUBSCRIBE, channelId, null, null, uuid()));
    assertThat(awaitFrame(client)).contains("Subscribe successfully");
  }

  private void unsubscribe(Client client, Long channelId) throws Exception {
    sendFrame(client, new WsClientFrame(WsMessageType.UNSUBSCRIBE, channelId, null, null, uuid()));
    assertThat(awaitFrame(client)).contains("\"type\":\"ACK\"");
  }

  /** Sends and waits for the ACK; fan-out to listeners finishes before the ACK is written. */
  private void sendAndAwaitAck(Client client, Long channelId, String content) throws Exception {
    send(client, channelId, content);
    assertThat(awaitFrame(client)).contains("\"type\":\"ACK\"");
  }

  private void send(Client client, Long channelId, String content) throws Exception {
    sendFrame(
        client,
        new WsClientFrame(
            WsMessageType.SEND_MESSAGE, channelId, ContentType.TEXT, content, uuid()));
  }

  private void sendFrame(Client client, WsClientFrame frame) throws Exception {
    client.session().sendMessage(new TextMessage(json.writeValueAsString(frame)));
  }

  private static String awaitFrame(Client client) throws InterruptedException {
    String frame = client.inbox().poll(TIMEOUT.toSeconds(), SECONDS);
    assertThat(frame).as("no frame within %s", TIMEOUT).isNotNull();
    return frame;
  }

  // --- seeding over REST (same flow as ChannelControllerAuthzIT) ---

  private Account signUp(String prefix) throws Exception {
    String username = prefix + "_" + uuid().substring(0, 10);
    var request =
        new RegisterUserRequest(username, PASSWORD, username + "@test.dev", "User " + prefix);
    MvcResult registered =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        ((Number) JsonPath.read(registered.getResponse().getContentAsString(), "$.data.userId"))
            .longValue();
    String accessToken = tokenIssuer.issue(userPort.findById(id).orElseThrow()).accessToken();
    return new Account(id, accessToken);
  }

  private Long createLounge(Account owner, Account... members) throws Exception {
    var request =
        new CreateChannelRequest(
            "lounge_" + uuid().substring(0, 10),
            BoundaryType.PUBLIC,
            null,
            ChannelType.LOUNGE,
            null,
            Arrays.stream(members).map(Account::id).toList());
    MvcResult created =
        mvc.perform(
                post("/api/v1/channels")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.channelId"))
        .longValue();
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
