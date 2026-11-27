package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

/** v2 READ: ACK {seq} with the marker after the call. */
@ExtendWith(MockitoExtension.class)
class ReadHandlerTest {

  @Mock private WebSocketSession session;
  @Mock private MarkChannelReadUseCase useCase;

  private V2Harness harness;

  @BeforeEach
  void setUp() {
    harness = new V2Harness(session, writer -> new ReadHandler(writer, useCase));
  }

  @Test
  void read_passesTheActorsSocket_andAcksTheEffectiveSeq() throws Exception {
    when(useCase.markRead(
            new MarkChannelReadCommand(V2Harness.CONNECTION, 42L, V2Harness.USER, 50L)))
        .thenReturn(30L);

    harness.dispatch(
        """
        {"v":2,"type":"READ","reqId":"r-1","channelId":42,"payload":{"seq":50}}
        """);

    JsonNode ack = harness.onlyReply();
    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("reqId").asText()).isEqualTo("r-1");
    assertThat(ack.path("payload").path("seq").asLong()).isEqualTo(30L);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"v\":2,\"type\":\"READ\",\"reqId\":\"r-2\",\"channelId\":42,\"payload\":{}}",
        "{\"v\":2,\"type\":\"READ\",\"reqId\":\"r-2\",\"payload\":{\"seq\":50}}"
      })
  void missingSeqOrChannelId_nacksInvalid_withoutCallingTheUseCase(String frame) throws Exception {
    harness.dispatch(frame);

    JsonNode nack = harness.onlyReply();
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INVALID");
    verifyNoInteractions(useCase);
  }
}
