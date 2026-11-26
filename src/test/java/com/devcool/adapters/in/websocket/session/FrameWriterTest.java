package com.devcool.adapters.in.websocket.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

class FrameWriterTest {

  private final FrameWriter writer = new FrameWriter(new ObjectMapper());

  @Test
  void ackOnOverflowedSession_closesItInsteadOfThrowing() throws Exception {
    WebSocketSession session = mock(WebSocketSession.class);
    doThrow(new SessionLimitExceededException("buffer full", CloseStatus.SESSION_NOT_RELIABLE))
        .when(session)
        .sendMessage(any(TextMessage.class));

    assertThatCode(() -> writer.ack(session, "r-1", "ok")).doesNotThrowAnyException();

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(session).close(CloseStatus.SESSION_NOT_RELIABLE));
  }
}
