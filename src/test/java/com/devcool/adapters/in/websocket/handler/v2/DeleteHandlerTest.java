package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.devcool.domain.chat.port.in.DeleteMessageUseCase;
import com.devcool.domain.chat.port.in.command.DeleteMessageCommand;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

/** v2 DELETE: frame → command → ACK. */
@ExtendWith(MockitoExtension.class)
class DeleteHandlerTest {

  @Mock private WebSocketSession session;
  @Mock private DeleteMessageUseCase useCase;

  private V2Harness harness;

  @BeforeEach
  void setUp() {
    harness = new V2Harness(session, writer -> new DeleteHandler(writer, useCase));
  }

  @Test
  void delete_passesTheActorsSocket_andAcks() throws Exception {
    harness.dispatch(
        """
        {"v":2,"type":"DELETE","reqId":"d-1","channelId":42,"payload":{"messageId":500}}
        """);

    verify(useCase).delete(new DeleteMessageCommand(V2Harness.CONNECTION, V2Harness.USER, 500L));
    JsonNode ack = harness.onlyReply();
    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("reqId").asText()).isEqualTo("d-1");
  }

  @Test
  void missingMessageId_nacksInvalid_withoutCallingTheUseCase() throws Exception {
    harness.dispatch("""
        {"v":2,"type":"DELETE","reqId":"d-2","payload":{}}
        """);

    JsonNode nack = harness.onlyReply();
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INVALID");
    verify(useCase, never()).delete(any());
  }
}
