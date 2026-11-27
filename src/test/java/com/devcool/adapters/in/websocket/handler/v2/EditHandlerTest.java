package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.domain.chat.exception.MessageVersionConflictException;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.EditMessageUseCase;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

/** v2 EDIT: frame → command → ACK {messageId, version, editedTime}. */
@ExtendWith(MockitoExtension.class)
class EditHandlerTest {

  private static final Instant EDITED = Instant.parse("2026-11-26T10:05:00Z");

  @Mock private WebSocketSession session;
  @Mock private EditMessageUseCase useCase;

  private V2Harness harness;

  @BeforeEach
  void setUp() {
    harness = new V2Harness(session, writer -> new EditHandler(writer, useCase));
  }

  @Test
  void edit_passesTheActorsSocket_andAcksTheNewVersion() throws Exception {
    when(useCase.edit(
            new EditMessageCommand(V2Harness.CONNECTION, V2Harness.USER, 500L, "fixed", 3)))
        .thenReturn(new EditedMessage(500L, 42L, 9L, ContentType.TEXT, "fixed", 4, EDITED));

    harness.dispatch(
        """
        {"v":2,"type":"EDIT","reqId":"e-1","channelId":42,
         "payload":{"messageId":500,"content":"fixed","expectedVersion":3}}
        """);

    JsonNode ack = harness.onlyReply();
    assertThat(ack.path("type").asText()).isEqualTo("ACK");
    assertThat(ack.path("reqId").asText()).isEqualTo("e-1");
    assertThat(ack.path("payload").path("messageId").asLong()).isEqualTo(500L);
    assertThat(ack.path("payload").path("version").asInt()).isEqualTo(4);
    assertThat(ack.path("payload").path("editedTime").asText()).isEqualTo("2026-11-26T10:05:00Z");
  }

  @Test
  void staleVersion_nacksConflict() throws Exception {
    when(useCase.edit(any())).thenThrow(new MessageVersionConflictException());

    harness.dispatch(
        """
        {"v":2,"type":"EDIT","reqId":"e-2",
         "payload":{"messageId":500,"content":"fixed","expectedVersion":2}}
        """);

    JsonNode nack = harness.onlyReply();
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("reqId").asText()).isEqualTo("e-2");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("CONFLICT");
  }

  @Test
  void missingMessageId_nacksInvalid_withoutCallingTheUseCase() throws Exception {
    harness.dispatch(
        """
        {"v":2,"type":"EDIT","reqId":"e-3","payload":{"content":"fixed","expectedVersion":3}}
        """);

    JsonNode nack = harness.onlyReply();
    assertThat(nack.path("type").asText()).isEqualTo("NACK");
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INVALID");
    verify(useCase, never()).edit(any());
  }

  @Test
  void missingExpectedVersion_nacksInvalid_withoutCallingTheUseCase() throws Exception {
    harness.dispatch(
        """
        {"v":2,"type":"EDIT","reqId":"e-4","payload":{"messageId":500,"content":"fixed"}}
        """);

    JsonNode nack = harness.onlyReply();
    assertThat(nack.path("payload").path("code").asText()).isEqualTo("INVALID");
    verify(useCase, never()).edit(any());
  }
}
