package com.devcool.adapters.in.websocket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.user.port.out.UserPort;
import com.devcool.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Virtual threads on the WS path (P1-T16), over a real socket. MockMvc can't prove this: it runs
 * the code on the JUnit thread, so this IT starts Tomcat on a random port (its own cached context,
 * unlike {@code AbstractIntegrationTest}). MockMvc is only used to seed users and the channel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(PostgresTestContainer.class)
class WsVirtualThreadsIT {

  private static final String PASSWORD = "Secret#123";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final int SENDS_PER_CLIENT = 20;

  @LocalServerPort int port;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;

  /** Records the thread of every SEND_MESSAGE, then runs the real use case. */
  @MockitoSpyBean SendMessageUseCase sendMessageUseCase;

  private final Queue<Thread> handlerThreads = new ConcurrentLinkedQueue<>();
  private final List<WebSocketSession> openSessions = new ArrayList<>();

  record Account(Integer id, String accessToken) {}

  /** A test-side WS connection; every text frame the server sends lands in {@code inbox}. */
  record Client(WebSocketSession session, BlockingQueue<String> inbox) {}

  @BeforeEach
  void recordHandlerThreads() {
    doAnswer(
            invocation -> {
              handlerThreads.add(Thread.currentThread());
              return invocation.callRealMethod();
            })
        .when(sendMessageUseCase)
        .sendMessage(any());
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
  void handlesSendMessageFramesOnVirtualThreads() throws Exception {
    Account sender = signUp("vs");
    Account receiver = signUp("vr");
    Integer channelId = createLounge(sender, receiver);

    Client listening = connect(receiver);
    subscribe(listening, channelId);
    Client sending = connect(sender);

    send(sending, channelId, "hello");

    assertThat(awaitFrame(sending)).contains("\"type\":\"ACK\"").contains("SENT");
    assertThat(awaitFrame(listening)).contains("\"type\":\"MESSAGE\"").contains("hello");
    assertThat(handlerThreads).singleElement().satisfies(t -> assertThat(t.isVirtual()).isTrue());
  }

  /**
   * Regression guard for pinning: a carrier must never stay pinned on the send path (a socket write
   * or JDBC call inside {@code synchronized} on Java 21). Localhost writes rarely block, so zero
   * events is a guard, not a proof; the Tomcat {@code javap} audit in the PR is the proof.
   *
   * <p>Senders don't subscribe, so each only ever writes its own ACKs. Two fan-outs can still hit
   * the receiver at once and one may be dropped (concurrent writes on one session, fixed by
   * P4-T01), so the receiver's frame count isn't asserted here.
   */
  @Test
  void concurrentSendsDoNotPinCarrierThreads() throws Exception {
    Account first = signUp("p1");
    Account second = signUp("p2");
    Account receiver = signUp("pr");
    Integer channelId = createLounge(first, second, receiver);

    Client listening = connect(receiver);
    subscribe(listening, channelId);
    List<Client> senders = List.of(connect(first), connect(second));

    Queue<RecordedEvent> pinned = new ConcurrentLinkedQueue<>();
    try (var jfr = new RecordingStream()) {
      jfr.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO).withStackTrace();
      jfr.onEvent("jdk.VirtualThreadPinned", pinned::add);
      jfr.startAsync();

      sendConcurrently(senders, channelId);
      for (Client sender : senders) {
        for (int i = 0; i < SENDS_PER_CLIENT; i++) {
          assertThat(awaitFrame(sender)).contains("\"type\":\"ACK\"");
        }
      }

      jfr.stop(); // flushes buffered events before we read them
    }

    assertThat(handlerThreads)
        .hasSize(senders.size() * SENDS_PER_CLIENT)
        .allSatisfy(t -> assertThat(t.isVirtual()).isTrue());
    assertThat(pinned).as("pinned virtual threads:%n%s", describe(pinned)).isEmpty();
  }

  private void sendConcurrently(List<Client> senders, Integer channelId) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(senders.size())) {
      List<Future<?>> done = new ArrayList<>();
      for (Client sender : senders) {
        done.add(
            pool.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < SENDS_PER_CLIENT; i++) {
                    send(sender, channelId, "msg-" + i);
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : done) {
        f.get(TIMEOUT.toSeconds(), SECONDS);
      }
    }
  }

  private static String describe(Queue<RecordedEvent> events) {
    StringBuilder out = new StringBuilder();
    events.forEach(e -> out.append(e.getStackTrace()).append(System.lineSeparator()));
    return out.toString();
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

  private void subscribe(Client client, Integer channelId) throws Exception {
    sendFrame(client, new WsClientFrame(WsMessageType.SUBSCRIBE, channelId, null, null, uuid()));
    assertThat(awaitFrame(client)).contains("Subscribe successfully");
  }

  private void send(Client client, Integer channelId, String content) throws Exception {
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
    Integer id = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.userId");
    String accessToken = tokenIssuer.issue(userPort.findById(id).orElseThrow()).accessToken();
    return new Account(id, accessToken);
  }

  private Integer createLounge(Account owner, Account... members) throws Exception {
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
    return JsonPath.read(created.getResponse().getContentAsString(), "$.data.channelId");
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }
}
