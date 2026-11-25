package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.chat.model.ClientMsgId;
import com.devcool.domain.chat.model.MessageEvent;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMessageServiceTest {

  private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");
  private static final SavedMessage SAVED = new SavedMessage(100L, 42L, CREATED, false);

  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @Mock private RealtimeEmitterPort realtimeEmitterPort;
  @Mock private SaveMessageUseCase saveMessageUseCase;
  @InjectMocks private SendMessageService service;

  @SuppressWarnings("unchecked")
  private final Consumer<SavedMessage> onCommitted = mock(Consumer.class);

  @Test
  void newSend_commitsThenAcksThenPushes() {
    when(saveMessageUseCase.save(any())).thenReturn(SAVED);
    when(connectionRegistryPort.getConnectionsByChannel(10L)).thenReturn(Set.of("conn-2"));

    service.sendMessage(command(), onCommitted);

    InOrder order = inOrder(saveMessageUseCase, onCommitted, realtimeEmitterPort);
    order.verify(saveMessageUseCase).save(any());
    order.verify(onCommitted).accept(SAVED);
    order.verify(realtimeEmitterPort).send(eq("conn-2"), any());
  }

  @Test
  void newSend_pushCarriesSavedIdSeqAndCreatedTime_andSkipsTheSender() {
    when(saveMessageUseCase.save(any())).thenReturn(SAVED);
    when(connectionRegistryPort.getConnectionsByChannel(10L))
        .thenReturn(Set.of("conn-1", "conn-2"));

    service.sendMessage(command(), onCommitted);

    MessageEvent event = pushedTo("conn-2");
    verify(realtimeEmitterPort, never()).send(eq("conn-1"), any());
    assertThat(event)
        .extracting(MessageEvent::messageId, MessageEvent::seq, MessageEvent::createdTime)
        .containsExactly(100L, 42L, CREATED);
  }

  @Test
  void ackFails_theCommittedMessageIsStillPushed_andTheFailurePropagates() {
    when(saveMessageUseCase.save(any())).thenReturn(SAVED);
    when(connectionRegistryPort.getConnectionsByChannel(10L)).thenReturn(Set.of("conn-2"));
    UncheckedIOException socketDied = new UncheckedIOException(new IOException("closed"));
    doThrow(socketDied).when(onCommitted).accept(SAVED);

    assertThatThrownBy(() -> service.sendMessage(command(), onCommitted)).isSameAs(socketDied);
    verify(realtimeEmitterPort).send(eq("conn-2"), any());
  }

  @Test
  void retriedClientMsgId_acksTheOriginal_andIsNotBroadcastAgain() {
    SavedMessage original = new SavedMessage(100L, 42L, CREATED, true);
    when(saveMessageUseCase.save(any())).thenReturn(original);

    service.sendMessage(command(), onCommitted);

    // The original send already reached the channel; a retry must not show the message twice.
    verify(onCommitted).accept(original);
    verifyNoInteractions(connectionRegistryPort, realtimeEmitterPort);
  }

  @Test
  void markdownSend_pushCarriesMarkdownContentType() {
    when(saveMessageUseCase.save(any())).thenReturn(SAVED);
    when(connectionRegistryPort.getConnectionsByChannel(10L)).thenReturn(Set.of("conn-2"));

    service.sendMessage(
        new SendMessageCommand(
            "conn-1",
            1L,
            10L,
            ContentType.MARKDOWN,
            "# hi **x**",
            new ClientMsgId(UUID.randomUUID())),
        onCommitted);

    assertThat(pushedTo("conn-2"))
        .extracting(MessageEvent::contentType, MessageEvent::content)
        .containsExactly(ContentType.MARKDOWN, "# hi **x**");
  }

  @Test
  void reply_isSavedAsAReply_andThePushCarriesReplyToId() {
    when(saveMessageUseCase.save(any())).thenReturn(SAVED);
    when(connectionRegistryPort.getConnectionsByChannel(10L)).thenReturn(Set.of("conn-2"));

    service.sendMessage(
        new SendMessageCommand(
            "conn-1", 1L, 10L, ContentType.TEXT, "re", new ClientMsgId(UUID.randomUUID()), 77L),
        onCommitted);

    ArgumentCaptor<CreateMessageCommand> saved =
        ArgumentCaptor.forClass(CreateMessageCommand.class);
    verify(saveMessageUseCase).save(saved.capture());
    assertThat(saved.getValue().replyToId()).isEqualTo(77L);
    assertThat(pushedTo("conn-2").replyToId()).isEqualTo(77L);
  }

  private MessageEvent pushedTo(String connectionId) {
    ArgumentCaptor<MessageEvent> event = ArgumentCaptor.forClass(MessageEvent.class);
    verify(realtimeEmitterPort).send(eq(connectionId), event.capture());
    return event.getValue();
  }

  private static SendMessageCommand command() {
    return new SendMessageCommand(
        "conn-1", 1L, 10L, ContentType.TEXT, "hello", new ClientMsgId(UUID.randomUUID()));
  }
}
