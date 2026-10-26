package com.devcool;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.LoginRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.adapters.in.websocket.dto.WsClientFrame;
import com.devcool.adapters.in.websocket.dto.WsMessageType;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Ids past 2^31 work end to end (P3-T01): every sequence is moved beyond {@link Integer#MAX_VALUE},
 * then register → login → refresh → create channel → WS subscribe + send → history must carry those
 * ids. Covers the casts that compile but fail at runtime (the WS handshake's principal check, the
 * token subject parsing) and Hibernate's validate against the V3 column types.
 *
 * <p>Hibernate hands out ids from blocks of 50 it reserved earlier, so a context that already
 * inserted rows would keep using small ids. The unique property gives this class its own context,
 * whose first insert per table calls {@code nextval} after the sequences moved.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "devcool.test.context=bigint-ids")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
class BigintIdsIT {

  private static final String PASSWORD = "Secret#123";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final long BEYOND_INT = 3_000_000_000L;
  private static final List<String> SEQUENCES =
      List.of("app_user_seq", "channel_seq", "member_seq", "message_seq");

  @LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  private final List<WebSocketSession> openSessions = new ArrayList<>();

  record Account(long id, String accessToken, String refreshCookie) {}

  record Client(WebSocketSession session, BlockingQueue<String> inbox) {}

  @BeforeEach
  void moveSequencesBeyondIntRange() {
    // greatest(): the container may be reused across runs, so never move a sequence backwards
    for (String sequence : SEQUENCES) {
      jdbc.queryForObject(
          "select setval(?::regclass, greatest(?, (select last_value from " + sequence + ")))",
          Long.class,
          sequence,
          BEYOND_INT);
    }
  }

  @AfterEach
  void closeSessions() throws Exception {
    for (WebSocketSession session : openSessions) {
      if (session.isOpen()) {
        session.close();
      }
    }
  }

  @Test
  void idsBeyondIntRange_roundTripThroughRestWebSocketAndHistory() throws Exception {
    Account sender = signUpAndLogIn("bs");
    Account receiver = signUpAndLogIn("br");
    assertThat(sender.id()).isGreaterThan(Integer.MAX_VALUE);

    // Refresh parses the user id back out of the refresh token's subject
    mvc.perform(post("/api/v1/auth/refresh_token").cookie(new Cookie("rt", sender.refreshCookie())))
        .andExpect(status().isOk());

    long channelId = createLounge(sender, receiver);
    assertThat(channelId).isGreaterThan(Integer.MAX_VALUE);

    // The handshake accepts a Long principal; with an Integer check it would reject the upgrade
    Client listening = connect(receiver);
    subscribe(listening, channelId);
    Client sending = connect(sender);
    sendFrame(
        sending,
        new WsClientFrame(
            WsMessageType.SEND_MESSAGE, channelId, ContentType.TEXT, "big ids", uuid()));

    assertThat(awaitFrame(sending)).contains("\"type\":\"ACK\"").contains("SENT");
    JsonNode pushed = json.readTree(awaitFrame(listening));
    assertThat(pushed.get("channelId").asLong()).isEqualTo(channelId);
    assertThat(pushed.get("senderId").asLong()).isEqualTo(sender.id());

    MvcResult history =
        mvc.perform(
                get("/api/v1/channels/{channelId}/messages", channelId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
            .andExpect(status().isOk())
            .andReturn();
    String body = history.getResponse().getContentAsString();
    assertThat(readLong(body, "$.data.items[0].id")).isGreaterThan(Integer.MAX_VALUE);
    assertThat(readLong(body, "$.data.items[0].userId")).isEqualTo(sender.id());
  }

  // --- seeding over REST ---

  private Account signUpAndLogIn(String prefix) throws Exception {
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
    long id = readLong(registered.getResponse().getContentAsString(), "$.data.userId");

    MvcResult loggedIn =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new LoginRequest(username, PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    String accessToken =
        JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.data.accessToken");
    String refreshCookie =
        Objects.requireNonNull(loggedIn.getResponse().getCookie("rt")).getValue();
    return new Account(id, accessToken, refreshCookie);
  }

  private long createLounge(Account owner, Account member) throws Exception {
    var request =
        new CreateChannelRequest(
            "lounge_" + uuid().substring(0, 10),
            BoundaryType.PUBLIC,
            null,
            ChannelType.LOUNGE,
            null,
            List.of(member.id()));
    MvcResult created =
        mvc.perform(
                post("/api/v1/channels")
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    return readLong(created.getResponse().getContentAsString(), "$.data.channelId");
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

  private void subscribe(Client client, long channelId) throws Exception {
    sendFrame(client, new WsClientFrame(WsMessageType.SUBSCRIBE, channelId, null, null, uuid()));
    assertThat(awaitFrame(client)).contains("Subscribe successfully");
  }

  private void sendFrame(Client client, WsClientFrame frame) throws Exception {
    client.session().sendMessage(new TextMessage(json.writeValueAsString(frame)));
  }

  private static String awaitFrame(Client client) throws InterruptedException {
    String frame = client.inbox().poll(TIMEOUT.toSeconds(), SECONDS);
    assertThat(frame).as("no frame within %s", TIMEOUT).isNotNull();
    return frame;
  }

  private static long readLong(String body, String path) {
    // JsonPath returns Integer or Long depending on the value's size
    return ((Number) JsonPath.read(body, path)).longValue();
  }

  private static String bearer(Account account) {
    return "Bearer " + account.accessToken();
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
