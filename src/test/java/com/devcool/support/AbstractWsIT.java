package com.devcool.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.request.CreateChannelRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.user.port.out.UserPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Base for ITs over a real WebSocket. MockMvc can't drive a socket, so Tomcat starts on a random
 * port (its own cached context, unlike {@code AbstractIntegrationTest}). MockMvc only seeds users
 * and channels over REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
public abstract class AbstractWsIT {

  protected static final String PASSWORD = "Secret#123";
  protected static final Duration TIMEOUT = Duration.ofSeconds(10);

  @LocalServerPort protected int port;
  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected TokenIssuerPort tokenIssuer;
  @Autowired protected UserPort userPort;

  private final List<WebSocketSession> openSessions = new ArrayList<>();

  protected record Account(Long id, String accessToken) {}

  /** Test-side WS connection; frames land in {@code inbox}, the close in {@code closed}. */
  protected record Client(
      WebSocketSession session,
      BlockingQueue<String> inbox,
      CompletableFuture<CloseStatus> closed) {}

  @AfterEach
  void closeSessions() throws Exception {
    for (WebSocketSession session : openSessions) {
      if (session.isOpen()) {
        session.close();
      }
    }
  }

  // --- WS client ---

  protected Client connect(Account account) throws Exception {
    BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
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

                  @Override
                  public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    closed.complete(status);
                  }
                },
                headers,
                URI.create("ws://localhost:" + port + "/ws"))
            .get(TIMEOUT.toSeconds(), SECONDS);
    openSessions.add(session);
    return new Client(session, inbox, closed);
  }

  protected void subscribe(Client client, Long channelId) throws Exception {
    String reqId = uuid();
    sendFrame(client, v2("SUBSCRIBE", channelId, reqId, null));
    assertAck(awaitFrame(client), reqId);
  }

  protected void unsubscribe(Client client, Long channelId) throws Exception {
    String reqId = uuid();
    sendFrame(client, v2("UNSUBSCRIBE", channelId, reqId, null));
    assertAck(awaitFrame(client), reqId);
  }

  /** Sends and waits for the ACK; fan-out to listeners finishes before the ACK is written. */
  protected void sendAndAwaitAck(Client client, Long channelId, String content) throws Exception {
    String reqId = send(client, channelId, content);
    assertAck(awaitFrame(client), reqId);
  }

  /** A v2 SEND with a fresh key; returns its reqId. */
  protected String send(Client client, Long channelId, String content) throws Exception {
    String reqId = uuid();
    sendFrame(
        client,
        v2(
            "SEND",
            channelId,
            reqId,
            Map.of("clientMsgId", uuid(), "contentType", "TEXT", "content", content)));
    return reqId;
  }

  protected void sendFrame(Client client, Object frame) throws Exception {
    client.session().sendMessage(new TextMessage(json.writeValueAsString(frame)));
  }

  /** A v2 envelope (ADR-0005). */
  protected static Map<String, Object> v2(
      String type, Long channelId, String reqId, Object payload) {
    Map<String, Object> frame = new LinkedHashMap<>();
    frame.put("v", 2);
    frame.put("type", type);
    frame.put("reqId", reqId);
    frame.put("channelId", channelId);
    frame.put("payload", payload);
    return frame;
  }

  private static void assertAck(String frame, String reqId) {
    assertThat(frame).contains("\"type\":\"ACK\"").contains("\"reqId\":\"" + reqId + "\"");
  }

  protected static String awaitFrame(Client client) throws InterruptedException {
    String frame = client.inbox().poll(TIMEOUT.toSeconds(), SECONDS);
    assertThat(frame).as("no frame within %s", TIMEOUT).isNotNull();
    return frame;
  }

  // --- seeding over REST (same flow as ChannelControllerAuthzIT) ---

  protected Account signUp(String prefix) throws Exception {
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

  protected Long createLounge(Account owner, Account... members) throws Exception {
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

  protected static String uuid() {
    return UUID.randomUUID().toString();
  }
}
