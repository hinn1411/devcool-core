package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.application.service.chat.WsSendMessageService.OutboundWsEvent;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WsSendMessageServiceTest {

  private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");

  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @Mock private RealtimeEmitterPort realtimeEmitterPort;
  @Mock private SaveMessageUseCase saveMessageUseCase;
  @InjectMocks private WsSendMessageService service;

  @Test
  void newSend_pushCarriesSavedIdSeqAndCreatedTime() {
    SavedMessage saved = new SavedMessage(100L, 42L, CREATED, false);
    when(saveMessageUseCase.save(any())).thenReturn(saved);
    when(connectionRegistryPort.getConnectionsByChannel(10L))
        .thenReturn(Set.of("conn-1", "conn-2"));

    assertThat(service.sendMessage(command())).isEqualTo(saved);

    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(realtimeEmitterPort).sendMessageToConnection(eq("conn-2"), payload.capture());
    verify(realtimeEmitterPort, never()).sendMessageToConnection(eq("conn-1"), any());
    assertThat((OutboundWsEvent) payload.getValue())
        .extracting(OutboundWsEvent::messageId, OutboundWsEvent::seq, OutboundWsEvent::createdTime)
        .containsExactly(100L, 42L, CREATED);
  }

  @Test
  void retriedClientMsgId_isNotBroadcastAgain_andReturnsTheOriginal() {
    SavedMessage original = new SavedMessage(100L, 42L, CREATED, true);
    when(saveMessageUseCase.save(any())).thenReturn(original);

    // The original send already reached the channel; a retry must not show the message twice.
    assertThat(service.sendMessage(command())).isEqualTo(original);
    verifyNoInteractions(connectionRegistryPort, realtimeEmitterPort);
  }

  @Test
  void markdownSend_pushCarriesMarkdownContentType() {
    when(saveMessageUseCase.save(any())).thenReturn(new SavedMessage(100L, 42L, CREATED, false));
    when(connectionRegistryPort.getConnectionsByChannel(10L)).thenReturn(Set.of("conn-2"));

    service.sendMessage(
        new SendMessageCommand(
            "conn-1", 1L, 10L, ContentType.MARKDOWN, "# hi **x**", UUID.randomUUID()));

    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(realtimeEmitterPort).sendMessageToConnection(eq("conn-2"), payload.capture());
    assertThat((OutboundWsEvent) payload.getValue())
        .extracting(OutboundWsEvent::contentType, OutboundWsEvent::content)
        .containsExactly("MARKDOWN", "# hi **x**");
  }

  private static SendMessageCommand command() {
    return new SendMessageCommand("conn-1", 1L, 10L, ContentType.TEXT, "hello", UUID.randomUUID());
  }
}
