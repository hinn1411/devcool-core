package com.devcool.adapters.in.websocket.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.adapters.in.websocket.config.WsSendLimitsProps;
import com.devcool.domain.chat.model.MessageEvent;
import com.devcool.domain.chat.model.enums.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.unit.DataSize;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

/** Push-path backpressure (P4-T01): real decorator over {@link FakeWsSession}s. */
class SessionEmitterAdapterTest {

  private static final Duration LONG = Duration.ofHours(1);
  private static final Duration NO_WAIT = Duration.ofSeconds(5);

  private final ObjectMapper objectMapper =
      Jackson2ObjectMapperBuilder.json()
          .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private final ExecutorService stuckSenders = Executors.newCachedThreadPool();
  private final List<FakeWsSession> blocked = new ArrayList<>();

  @AfterEach
  void releaseStuckSenders() {
    blocked.forEach(FakeWsSession::unblock);
    stuckSenders.shutdownNow();
  }

  @Test
  void concurrentWritesToOneSession_bothArriveIntact() throws Exception {
    SessionRegistry registry = registry(LONG, DataSize.ofMegabytes(1));
    SessionEmitterAdapter emitter = emitter(registry);
    FakeWsSession alice = registered(registry, "alice");
    pushStuckOn(alice, emitter);

    emitter.send("alice", event(2L));
    alice.unblock();

    await()
        .atMost(NO_WAIT)
        .untilAsserted(
            () ->
                assertThat(alice.received())
                    .hasSize(2)
                    .anySatisfy(f -> assertThat(f).contains("\"seq\":1"))
                    .anySatisfy(f -> assertThat(f).contains("\"seq\":2")));
    assertThat(alice.closeStatus()).isNull();
  }

  @Test
  void blockedRecipient_othersStillReceive_andItIsClosedWith4500OnBufferOverflow()
      throws Exception {
    SessionRegistry registry = registry(LONG, DataSize.ofBytes(1));
    SessionEmitterAdapter emitter = emitter(registry);
    FakeWsSession alice = registered(registry, "alice");
    FakeWsSession bob = registered(registry, "bob");
    FakeWsSession carol = registered(registry, "carol");
    pushStuckOn(bob, emitter);

    assertTimeoutPreemptively(
        NO_WAIT,
        () -> {
          for (String id : List.of("alice", "bob", "carol")) {
            emitter.send(id, event(2L));
          }
        });

    assertThat(alice.received()).singleElement().asString().contains("\"seq\":2");
    assertThat(carol.received()).singleElement().asString().contains("\"seq\":2");
    await()
        .atMost(NO_WAIT)
        .untilAsserted(
            () -> assertThat(bob.closeStatus()).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE));
    assertThat(bob.closeStatus().getCode()).isEqualTo(4500);
  }

  @Test
  void overflowClose_runsOffTheCallerThread() throws Exception {
    SessionRegistry registry = registry(LONG, DataSize.ofBytes(1));
    SessionEmitterAdapter emitter = emitter(registry);
    FakeWsSession bob = registered(registry, "bob");
    pushStuckOn(bob, emitter);

    emitter.send("bob", event(2L));

    await().atMost(NO_WAIT).untilAsserted(() -> assertThat(bob.closeThread()).isNotNull());
    assertThat(bob.closeThread()).isNotSameAs(Thread.currentThread());
  }

  @Test
  void blockedRecipient_isClosedWith4500OnSendTimeLimit() throws Exception {
    SessionRegistry registry = registry(Duration.ofMillis(1), DataSize.ofMegabytes(1));
    SessionEmitterAdapter emitter = emitter(registry);
    FakeWsSession bob = registered(registry, "bob");
    pushStuckOn(bob, emitter);

    await()
        .atMost(NO_WAIT)
        .until(
            () -> {
              emitter.send("bob", event(2L));
              return bob.closeStatus() != null;
            });

    assertThat(bob.closeStatus()).isEqualTo(CloseStatus.SESSION_NOT_RELIABLE);
  }

  @Test
  void limitExceeded_closesSession_andDoesNotThrow() throws Exception {
    WebSocketSession session = openSession();
    doThrow(new SessionLimitExceededException("buffer full", CloseStatus.SESSION_NOT_RELIABLE))
        .when(session)
        .sendMessage(any(TextMessage.class));

    assertThatCode(() -> emitter(registryWith(session)).send("conn-1", event(1L)))
        .doesNotThrowAnyException();

    awaitClosed(session);
  }

  @Test
  void anyRuntimeFailure_closesSession_andDoesNotThrow() throws Exception {
    WebSocketSession session = openSession();
    doThrow(new UncheckedIOException(new IOException("broken pipe")))
        .when(session)
        .sendMessage(any(TextMessage.class));

    assertThatCode(() -> emitter(registryWith(session)).send("conn-1", event(1L)))
        .doesNotThrowAnyException();

    awaitClosed(session);
  }

  @Test
  void ioFailure_closesSession() throws Exception {
    WebSocketSession session = openSession();
    doThrow(new IOException("write timed out")).when(session).sendMessage(any(TextMessage.class));

    emitter(registryWith(session)).send("conn-1", event(1L));

    awaitClosed(session);
  }

  // --- fixtures ---

  private static void awaitClosed(WebSocketSession session) {
    await()
        .atMost(NO_WAIT)
        .untilAsserted(() -> verify(session).close(CloseStatus.SESSION_NOT_RELIABLE));
  }

  /** Pushes seq 1 on another thread and waits until it hangs in the write. */
  private void pushStuckOn(FakeWsSession session, SessionEmitterAdapter emitter)
      throws InterruptedException {
    session.block();
    blocked.add(session);
    stuckSenders.execute(() -> emitter.send(session.getId(), event(1L)));
    session.awaitSendStarted();
  }

  private static SessionRegistry registry(Duration timeLimit, DataSize bufferSizeLimit) {
    return new SessionRegistry(new WsSendLimitsProps(timeLimit, bufferSizeLimit, LONG));
  }

  private static FakeWsSession registered(SessionRegistry registry, String id) {
    FakeWsSession session = new FakeWsSession(id);
    registry.register(session);
    return session;
  }

  private static SessionRegistry registryWith(WebSocketSession session) {
    SessionRegistry registry = mock(SessionRegistry.class);
    when(registry.get("conn-1")).thenReturn(session);
    return registry;
  }

  private static WebSocketSession openSession() {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private SessionEmitterAdapter emitter(SessionRegistry registry) {
    return new SessionEmitterAdapter(registry, new FrameWriter(objectMapper));
  }

  private static MessageEvent event(Long seq) {
    return new MessageEvent(
        42L,
        7L,
        ContentType.TEXT,
        "hi",
        100L + seq,
        seq,
        Instant.parse("2026-11-26T10:00:00Z"),
        null);
  }
}
