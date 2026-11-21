package com.devcool.adapters.in.websocket;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.support.AbstractWsIT;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Virtual threads on the WS path (P1-T16), over a real socket. MockMvc can't prove this: it runs
 * the code on the JUnit thread.
 */
class WsVirtualThreadsIT extends AbstractWsIT {

  private static final int SENDS_PER_CLIENT = 20;

  /** Records the thread of every SEND_MESSAGE, then runs the real use case. */
  @MockitoSpyBean SendMessageUseCase sendMessageUseCase;

  private final Queue<Thread> handlerThreads = new ConcurrentLinkedQueue<>();

  @BeforeEach
  void recordHandlerThreads() {
    doAnswer(
            invocation -> {
              handlerThreads.add(Thread.currentThread());
              return invocation.callRealMethod();
            })
        .when(sendMessageUseCase)
        .sendMessage(any(), any());
  }

  @Test
  void handlesSendMessageFramesOnVirtualThreads() throws Exception {
    Account sender = signUp("vs");
    Account receiver = signUp("vr");
    Long channelId = createLounge(sender, receiver);

    Client listening = connect(receiver);
    subscribe(listening, channelId);
    Client sending = connect(sender);

    send(sending, channelId, "hello");

    assertThat(awaitFrame(sending)).contains("\"type\":\"ACK\"").contains("\"messageId\"");
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
    Long channelId = createLounge(first, second, receiver);

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

  private void sendConcurrently(List<Client> senders, Long channelId) throws Exception {
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
}
