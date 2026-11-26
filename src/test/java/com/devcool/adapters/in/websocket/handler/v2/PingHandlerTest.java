package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** PING → one PONG with the same reqId, never a close (P4-T03). */
@ExtendWith(MockitoExtension.class)
class PingHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private WebSocketSession session;

  private FrameDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    FrameWriter writer = new FrameWriter(objectMapper);
    dispatcher = new FrameDispatcher(objectMapper, writer, List.of(new PingHandler(writer)));
    lenient().when(session.getId()).thenReturn("conn-1");
  }

  @Test
  void ping_repliesOnePongWithTheReqId_andKeepsTheSocketOpen() throws Exception {
    dispatch("{\"v\":2,\"type\":\"PING\",\"reqId\":\"p-1\"}");

    JsonNode reply = onlyReply();
    assertThat(reply.path("v").asInt()).isEqualTo(2);
    assertThat(reply.path("type").asText()).isEqualTo("PONG");
    assertThat(reply.path("reqId").asText()).isEqualTo("p-1");
    verify(session, never()).close(any(CloseStatus.class));
  }

  /** Brief decision: reqId is required (ADR-0005 rule 1). */
  @Test
  void pingWithoutReqId_nacksInvalid() throws Exception {
    dispatch("{\"v\":2,\"type\":\"PING\"}");

    JsonNode reply = onlyReply();
    assertThat(reply.path("type").asText()).isEqualTo("NACK");
    assertThat(reply.path("payload").path("code").asText()).isEqualTo("INVALID");
    assertThat(reply.hasNonNull("reqId")).isFalse();
  }

  private void dispatch(String json) throws Exception {
    dispatcher.dispatch(session, objectMapper.readTree(json));
  }

  private JsonNode onlyReply() throws Exception {
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(sent.capture());
    return objectMapper.readTree(sent.getValue().getPayload());
  }
}
