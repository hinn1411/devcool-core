package com.devcool.adapters.in.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.support.AbstractWsIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.CloseStatus;

/**
 * Heartbeat and server idle close (P4-T03). A 2 s idle timeout keeps the run short; Tomcat checks
 * expiry on its background period (~10 s), so a close lands within timeout + period.
 */
@TestPropertySource(properties = "devcool.ws.idle-timeout=2s")
class WsHeartbeatIT extends AbstractWsIT {

  /** Idle timeout + Tomcat's background period + slack. */
  private static final Duration CLOSE_WITHIN = Duration.ofSeconds(25);

  @Autowired ConnectionRegistryPort connectionRegistry;

  @Test
  void ping_getsOnePongWithTheReqId() throws Exception {
    Client client = connect(signUp("hbp"));

    sendFrame(client, v2("PING", null, "p-1", null));

    JsonNode pong = json.readTree(awaitFrame(client));
    assertThat(pong.path("v").asInt()).isEqualTo(2);
    assertThat(pong.path("type").asText()).isEqualTo("PONG");
    assertThat(pong.path("reqId").asText()).isEqualTo("p-1");
    assertThat(client.inbox().poll(300, TimeUnit.MILLISECONDS)).as("exactly one reply").isNull();
    assertThat(client.session().isOpen()).isTrue();
  }

  @Test
  void pingWithoutReqId_nacksInvalid_andTheSocketStaysOpen() throws Exception {
    Client client = connect(signUp("hbn"));

    sendFrame(client, v2("PING", null, null, null));

    JsonNode nack = json.readTree(awaitFrame(client));
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INVALID");
    assertThat(client.session().isOpen()).isTrue();
  }

  @Test
  void idleConnection_isClosedWithGoingAway_andItsSubscriptionsAreCleanedUp() throws Exception {
    Account owner = signUp("hbi");
    Long channelId = createLounge(owner, signUp("hbm"));
    Client idle = connect(owner);
    subscribe(idle, channelId);
    assertThat(connectionRegistry.getConnectionsByChannel(channelId)).hasSize(1);

    assertThat(idle.closed())
        .as("server idle close")
        .succeedsWithin(CLOSE_WITHIN)
        .extracting(CloseStatus::getCode)
        .isEqualTo(CloseStatus.GOING_AWAY.getCode());
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(connectionRegistry.getConnectionsByChannel(channelId)).isEmpty());
  }

  /** Server pushes don't reset the clock (read-idle); the client's own frames do. */
  @Test
  void pushOnlyConnection_stillCloses_whileTheActiveSenderStaysOpen() throws Exception {
    Account sender = signUp("hbs");
    Account receiver = signUp("hbr");
    Long channelId = createLounge(sender, receiver);
    Client listening = connect(receiver);
    subscribe(listening, channelId);
    Client sending = connect(sender);

    await()
        .atMost(CLOSE_WITHIN)
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              sendAndAwaitAck(sending, channelId, "keep-alive");
              assertThat(listening.closed()).as("push-only client closed").isDone();
            });

    assertThat(listening.closed().get().getCode()).isEqualTo(CloseStatus.GOING_AWAY.getCode());
    List<String> received = new ArrayList<>(listening.inbox());
    assertThat(received).as("pushes reached the idle client").isNotEmpty();
    assertThat(sending.session().isOpen()).isTrue();
    sendFrame(sending, v2("PING", null, "p-2", null));
    assertThat(json.readTree(awaitFrame(sending)).path("type").asText()).isEqualTo("PONG");
  }
}
