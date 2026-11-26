package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.devcool.domain.chat.port.in.ReactToMessageUseCase;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

/** v2 REACT (P4-T04): {@code on} picks react or unreact; then ACK. */
@ExtendWith(MockitoExtension.class)
class ReactHandlerTest {

  private static final ReactionCommand THUMBS =
      new ReactionCommand(V2Harness.CONNECTION, V2Harness.USER, 500L, "👍");

  @Mock private WebSocketSession session;
  @Mock private ReactToMessageUseCase useCase;

  private V2Harness harness;

  @BeforeEach
  void setUp() {
    harness = new V2Harness(session, writer -> new ReactHandler(writer, useCase));
  }

  @Test
  void onTrue_reacts_andAcks() throws Exception {
    harness.dispatch(
        """
        {"v":2,"type":"REACT","reqId":"x-1","payload":{"messageId":500,"emoji":"👍","on":true}}
        """);

    verify(useCase).react(THUMBS);
    assertAck("x-1");
  }

  @Test
  void onFalse_unreacts_andAcks() throws Exception {
    harness.dispatch(
        """
        {"v":2,"type":"REACT","reqId":"x-2","payload":{"messageId":500,"emoji":"👍","on":false}}
        """);

    verify(useCase).unreact(THUMBS);
    assertAck("x-2");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"emoji\":\"👍\",\"on\":true}",
        "{\"messageId\":500,\"on\":true}",
        "{\"messageId\":500,\"emoji\":\"👍\"}"
      })
  void missingField_nacksInvalid_withoutCallingTheUseCase(String payload) throws Exception {
    harness.dispatch("{\"v\":2,\"type\":\"REACT\",\"reqId\":\"x-3\",\"payload\":" + payload + "}");

    JsonNode nack = harness.onlyReply();
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INVALID");
    verifyNoInteractions(useCase);
  }

  private void assertAck(String reqId) throws Exception {
    JsonNode ack = harness.onlyReply();
    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("reqId").asText()).isEqualTo(reqId);
  }
}
