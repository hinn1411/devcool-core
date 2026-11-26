package com.devcool.adapters.in.websocket.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devcool.adapters.in.websocket.config.WsSendLimitsProps;
import jakarta.websocket.Session;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

class SessionRegistryTest {

  private static final WsSendLimitsProps LIMITS =
      new WsSendLimitsProps(
          Duration.ofSeconds(5), DataSize.ofKilobytes(512), Duration.ofSeconds(5));

  private final SessionRegistry registry = new SessionRegistry(LIMITS);

  @Test
  void registerStoresADecoratorWithConfiguredLimits() {
    FakeWsSession raw = new FakeWsSession("conn-1");

    WebSocketSession wrapped = registry.register(raw);

    assertThat(wrapped).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
    ConcurrentWebSocketSessionDecorator decorator = (ConcurrentWebSocketSessionDecorator) wrapped;
    assertThat(decorator.getSendTimeLimit()).isEqualTo(5_000);
    assertThat(decorator.getBufferSizeLimit()).isEqualTo(512 * 1024);
    assertThat(decorator.getDelegate()).isSameAs(raw);
    assertThat(registry.get("conn-1")).isSameAs(wrapped);
  }

  @Test
  void registerSetsTomcatBlockingSendTimeout() {
    Map<String, Object> userProperties = new HashMap<>();
    Session nativeSession = mock(Session.class);
    when(nativeSession.getUserProperties()).thenReturn(userProperties);
    NativeWebSocketSession raw = mock(NativeWebSocketSession.class);
    when(raw.getId()).thenReturn("conn-1");
    when(raw.getNativeSession(Session.class)).thenReturn(nativeSession);

    registry.register(raw);

    assertThat(userProperties)
        .containsEntry("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 5_000L);
  }

  @Test
  void removeTwice_isANoOp() {
    registry.register(new FakeWsSession("conn-1"));

    registry.remove("conn-1");
    registry.remove("conn-1");

    assertThat(registry.get("conn-1")).isNull();
  }
}
