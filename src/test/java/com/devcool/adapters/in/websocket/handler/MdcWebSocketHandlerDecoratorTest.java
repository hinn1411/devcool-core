package com.devcool.adapters.in.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.devcool.adapters.in.websocket.security.WsAuthHandShakeInterceptor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class MdcWebSocketHandlerDecoratorTest {

  @Mock private WebSocketHandler delegate;
  @Mock private WebSocketSession session;

  private final Map<String, Object> attributes = new HashMap<>();
  private MdcWebSocketHandlerDecorator decorator;

  @BeforeEach
  void setUp() {
    decorator = new MdcWebSocketHandlerDecorator(delegate);
    attributes.put(WsAuthHandShakeInterceptor.ATTR_USER_ID, 42);
    when(session.getId()).thenReturn("conn-1");
    when(session.getAttributes()).thenReturn(attributes);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void handleMessage_delegateSeesUserIdAndConnectionId_andMdcIsEmptyAfterwards() throws Exception {
    AtomicReference<Map<String, String>> seen = new AtomicReference<>();
    doAnswer(inv -> seen.getAndSet(MDC.getCopyOfContextMap()))
        .when(delegate)
        .handleMessage(any(), any());

    decorator.handleMessage(session, new TextMessage("{}"));

    assertThat(seen.get()).containsEntry("userId", "42").containsEntry("connectionId", "conn-1");
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void handleMessage_delegateThrows_mdcIsStillClearedAndExceptionPropagates() throws Exception {
    doAnswer(
            inv -> {
              throw new IllegalStateException("boom");
            })
        .when(delegate)
        .handleMessage(any(), any());

    assertThatThrownBy(() -> decorator.handleMessage(session, new TextMessage("{}")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void connectAndClose_delegateSeesConnectionFields() throws Exception {
    AtomicReference<String> onOpen = new AtomicReference<>();
    AtomicReference<String> onClose = new AtomicReference<>();
    doAnswer(inv -> onOpen.getAndSet(MDC.get("connectionId")))
        .when(delegate)
        .afterConnectionEstablished(session);
    doAnswer(inv -> onClose.getAndSet(MDC.get("userId")))
        .when(delegate)
        .afterConnectionClosed(session, CloseStatus.NORMAL);

    decorator.afterConnectionEstablished(session);
    decorator.afterConnectionClosed(session, CloseStatus.NORMAL);

    assertThat(onOpen.get()).isEqualTo("conn-1");
    assertThat(onClose.get()).isEqualTo("42");
  }

  @Test
  void handleMessage_keepsMdcEntriesItDidNotSet() throws Exception {
    MDC.put("trace_id", "abc");

    decorator.handleMessage(session, new TextMessage("{}"));

    assertThat(MDC.getCopyOfContextMap()).containsExactly(Map.entry("trace_id", "abc"));
  }

  @Test
  void handleMessage_withoutUserId_setsOnlyConnectionId() throws Exception {
    attributes.clear();
    AtomicReference<Map<String, String>> seen = new AtomicReference<>();
    doAnswer(inv -> seen.getAndSet(MDC.getCopyOfContextMap()))
        .when(delegate)
        .handleMessage(any(), any());

    decorator.handleMessage(session, new TextMessage("{}"));

    assertThat(seen.get()).containsOnlyKeys("connectionId");
  }
}
