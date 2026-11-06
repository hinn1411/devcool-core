package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.application.service.chat.strategy.TextMessageCreationStrategy;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.exception.DuplicateClientMsgIdException;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** Seq assignment and idempotent send (P3-T03, ADR-0012). */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  private static final long CHANNEL = 1L, OTHER_CHANNEL = 2L, ALICE = 10L;
  private static final UUID KEY = UUID.fromString("7f1c2a9e-0000-4000-8000-000000000001");
  private static final Instant ORIGINAL_TIME = Instant.parse("2026-11-01T10:00:00Z");

  @Mock private MessagePort messagePort;
  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private final CountingTransactions transactions = new CountingTransactions();
  private MessageService service;

  @BeforeEach
  void setUp() {
    service =
        new MessageService(
            List.of(new TextMessageCreationStrategy()),
            messagePort,
            channelPort,
            new ChannelAuthorizer(channelPort, memberPort),
            transactions);
  }

  @Test
  void firstSend_takesSeqFromChannelCounter_andSavesItWithTheKey() {
    aliceIsMember();
    when(messagePort.findByClientMsgId(ALICE, KEY)).thenReturn(Optional.empty());
    when(channelPort.nextSeq(CHANNEL)).thenReturn(42L);
    when(messagePort.save(any())).thenReturn(100L);

    SavedMessage saved = service.save(text(CHANNEL));

    ArgumentCaptor<Message> stored = ArgumentCaptor.forClass(Message.class);
    verify(messagePort).save(stored.capture());
    assertThat(stored.getValue().getSeq()).isEqualTo(42L);
    assertThat(stored.getValue().getClientMsgId()).isEqualTo(KEY);
    assertThat(stored.getValue().getChannelId()).isEqualTo(CHANNEL);
    assertThat(stored.getValue().getSenderId()).isEqualTo(ALICE);
    assertThat(saved.id()).isEqualTo(100L);
    assertThat(saved.seq()).isEqualTo(42L);
    assertThat(saved.createdTime()).isEqualTo(stored.getValue().getCreatedTime()).isNotNull();
    assertThat(saved.duplicate()).isFalse();
  }

  @Test
  void returnsSameSeqOnRetriedClientMsgId_withoutBumpOrInsert() {
    aliceIsMember();
    when(messagePort.findByClientMsgId(ALICE, KEY)).thenReturn(Optional.of(existing(CHANNEL)));

    SavedMessage saved = service.save(text(CHANNEL));

    assertThat(saved).isEqualTo(new SavedMessage(100L, 42L, ORIGINAL_TIME, true));
    verify(channelPort, never()).nextSeq(anyLong());
    verify(messagePort, never()).save(any());
  }

  @Test
  void raceOnClientMsgId_reReadsTheWinnerInANewTransaction() {
    aliceIsMember();
    // 1st attempt: no row yet → bump → insert loses the race. 2nd read: the winner is committed.
    when(messagePort.findByClientMsgId(ALICE, KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing(CHANNEL)));
    when(channelPort.nextSeq(CHANNEL)).thenReturn(43L);
    when(messagePort.save(any())).thenThrow(new DuplicateClientMsgIdException());

    SavedMessage saved = service.save(text(CHANNEL));

    assertThat(saved).isEqualTo(new SavedMessage(100L, 42L, ORIGINAL_TIME, true));
    // The failed transaction is aborted (and its bump rolled back), so the re-read needs its own.
    assertThat(transactions.started).isEqualTo(2);
  }

  @Test
  void nonMember_isRejectedBeforeAnyBump() {
    when(channelPort.existById(CHANNEL)).thenReturn(true);
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);

    assertThatThrownBy(() -> service.save(text(CHANNEL)))
        .isInstanceOf(MemberNotFoundException.class);
    verify(channelPort, never()).nextSeq(anyLong());
    verifyNoInteractions(messagePort);
  }

  @Test
  void keyReusedInAnotherChannel_isRejectedWithoutBump() {
    aliceIsMember();
    when(messagePort.findByClientMsgId(ALICE, KEY))
        .thenReturn(Optional.of(existing(OTHER_CHANNEL)));

    assertThatThrownBy(() -> service.save(text(CHANNEL)))
        .isInstanceOf(ClientMsgIdReusedException.class);
    verify(channelPort, never()).nextSeq(anyLong());
    verify(messagePort, never()).save(any());
  }

  @Test
  void contentTypeWithoutStrategy_isRejectedBeforeAnyBump() {
    CreateMessageCommand markdown =
        new CreateMessageCommand("# hi", ContentType.MARKDOWN, CHANNEL, ALICE, KEY);

    assertThatThrownBy(() -> service.save(markdown))
        .isInstanceOf(InvalidMessageConfigException.class);
    verifyNoInteractions(messagePort, channelPort);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private void aliceIsMember() {
    when(channelPort.existById(CHANNEL)).thenReturn(true);
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(true);
  }

  private static CreateMessageCommand text(long channelId) {
    return new CreateMessageCommand("hello", ContentType.TEXT, channelId, ALICE, KEY);
  }

  /** The message Alice already stored under {@link #KEY}. */
  private static Message existing(long channelId) {
    return Message.builder()
        .id(100L)
        .seq(42L)
        .channelId(channelId)
        .senderId(ALICE)
        .clientMsgId(KEY)
        .contentType(ContentType.TEXT)
        .content("hello")
        .createdTime(ORIGINAL_TIME)
        .build();
  }

  /** Runs callbacks inline and counts how many transactions the service opened. */
  private static final class CountingTransactions implements TransactionOperations {
    int started;

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      started++;
      TransactionStatus status = new SimpleTransactionStatus();
      return action.doInTransaction(status);
    }
  }
}
