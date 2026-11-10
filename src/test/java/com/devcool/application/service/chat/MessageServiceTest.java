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
import com.devcool.application.service.chat.strategy.MarkdownMessageCreationStrategy;
import com.devcool.application.service.chat.strategy.TextMessageCreationStrategy;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.exception.InvalidHistoryCursorException;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageKey;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Seq assignment and idempotent send (P3-T03, ADR-0012); history paging (P3-T05); markdown and
 * content rules (P3-T15).
 */
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
    when(channelPort.getNextSequence(CHANNEL)).thenReturn(Optional.of(42L));
    when(messagePort.save(any())).thenReturn(Optional.of(100L));

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
    verify(channelPort, never()).getNextSequence(anyLong());
    verify(messagePort, never()).save(any());
  }

  @Test
  void raceOnClientMsgId_reReadsTheWinnerInANewTransaction() {
    aliceIsMember();
    // 1st attempt: no row yet → bump → insert loses the race. 2nd read: the winner is committed.
    when(messagePort.findByClientMsgId(ALICE, KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing(CHANNEL)));
    when(channelPort.getNextSequence(CHANNEL)).thenReturn(Optional.of(43L));
    when(messagePort.save(any())).thenReturn(Optional.empty()); // key taken by the winner

    SavedMessage saved = service.save(text(CHANNEL));

    assertThat(saved).isEqualTo(new SavedMessage(100L, 42L, ORIGINAL_TIME, true));
    // The failed transaction is aborted (and its bump rolled back), so the re-read needs its own.
    assertThat(transactions.started).isEqualTo(2);
    // The losing attempt must roll back, or its commit fails on the aborted transaction.
    assertThat(transactions.rolledBack).isEqualTo(1);
  }

  @Test
  void nonMember_isRejectedBeforeAnyBump() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);

    assertThatThrownBy(() -> service.save(text(CHANNEL)))
        .isInstanceOf(MemberNotFoundException.class);
    verify(channelPort, never()).getNextSequence(anyLong());
    verifyNoInteractions(messagePort);
  }

  @Test
  void unknownChannel_getsTheSameAnswerAsNonMember() {
    // No member row can exist without its channel (FK), so this also covers a missing channel.
    when(memberPort.existMemberOfChannelByUserId(OTHER_CHANNEL, ALICE)).thenReturn(false);

    // A distinct "channel not found" would let any user map which channel ids exist.
    assertThatThrownBy(() -> service.save(text(OTHER_CHANNEL)))
        .isInstanceOf(MemberNotFoundException.class);
    verifyNoInteractions(channelPort, messagePort);
  }

  @Test
  void keyReusedInAnotherChannel_isRejectedWithoutBump() {
    aliceIsMember();
    when(messagePort.findByClientMsgId(ALICE, KEY))
        .thenReturn(Optional.of(existing(OTHER_CHANNEL)));

    assertThatThrownBy(() -> service.save(text(CHANNEL)))
        .isInstanceOf(ClientMsgIdReusedException.class);
    verify(channelPort, never()).getNextSequence(anyLong());
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

  // ---- markdown + content rules (P3-T15) ------------------------------------------------------

  @Test
  void markdownSend_getsTheNextSeq_andStoresTheRawSource() {
    withMarkdown();
    aliceIsMember();
    when(messagePort.findByClientMsgId(ALICE, KEY)).thenReturn(Optional.empty());
    when(channelPort.getNextSequence(CHANNEL)).thenReturn(Optional.of(7L));
    when(messagePort.save(any())).thenReturn(Optional.of(100L));

    SavedMessage saved = service.save(send(ContentType.MARKDOWN, "# hi **x**"));

    ArgumentCaptor<Message> stored = ArgumentCaptor.forClass(Message.class);
    verify(messagePort).save(stored.capture());
    assertThat(stored.getValue().getContentType()).isEqualTo(ContentType.MARKDOWN);
    assertThat(stored.getValue().getContent()).isEqualTo("# hi **x**");
    assertThat(stored.getValue().getSeq()).isEqualTo(7L);
    assertThat(saved.seq()).isEqualTo(7L);
  }

  @Test
  void markdownSend_byNonMember_isRejectedBeforeAnyBump() {
    withMarkdown();
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);

    assertThatThrownBy(() -> service.save(send(ContentType.MARKDOWN, "# hi")))
        .isInstanceOf(MemberNotFoundException.class);
    verify(channelPort, never()).getNextSequence(anyLong());
    verifyNoInteractions(messagePort);
  }

  @ParameterizedTest
  @MethodSource("invalidContent")
  void invalidContent_isRejectedBeforeAnyPortCall(ContentType type, String content) {
    withMarkdown();

    assertThatThrownBy(() -> service.save(send(type, content)))
        .isInstanceOf(InvalidMessageConfigException.class);
    verifyNoInteractions(messagePort, channelPort, memberPort);
    assertThat(transactions.started).isZero();
  }

  /** PG {@code varchar(1000)} counts characters (code points), not UTF-16 units. */
  @ParameterizedTest
  @EnumSource(
      value = ContentType.class,
      names = {"TEXT", "MARKDOWN"})
  void exactly1000CodePoints_isAccepted(ContentType type) {
    withMarkdown();
    aliceIsMember();
    when(messagePort.findByClientMsgId(ALICE, KEY)).thenReturn(Optional.empty());
    when(channelPort.getNextSequence(CHANNEL)).thenReturn(Optional.of(1L));
    when(messagePort.save(any())).thenReturn(Optional.of(100L));
    String emoji = "\uD83D\uDE42".repeat(1000); // 1000 code points, 2000 chars

    assertThat(service.save(send(type, emoji)).seq()).isEqualTo(1L);
  }

  static Stream<Arguments> invalidContent() {
    return Stream.of(ContentType.TEXT, ContentType.MARKDOWN)
        .flatMap(
            type ->
                Stream.of(
                    Arguments.of(type, null),
                    Arguments.of(type, ""),
                    Arguments.of(type, " \n\t "),
                    Arguments.of(type, "x".repeat(1001))));
  }

  private void withMarkdown() {
    service =
        new MessageService(
            List.of(new TextMessageCreationStrategy(), new MarkdownMessageCreationStrategy()),
            messagePort,
            channelPort,
            new ChannelAuthorizer(channelPort, memberPort),
            transactions);
  }

  private static CreateMessageCommand send(ContentType type, String content) {
    return new CreateMessageCommand(content, type, CHANNEL, ALICE, KEY);
  }

  // ---- history paging (P3-T05) ----------------------------------------------------------------

  @Test
  void beforeSeq_extraRowMeansHasMore() {
    aliceCanRead();
    when(messagePort.findBeforeSeq(CHANNEL, 4L, 3)).thenReturn(seqs(3, 2, 1));

    MessageList page = service.getMessages(history(null, 4L, null, 2));

    assertThat(page.items()).extracting(MessageItem::getSeq).containsExactly(3L, 2L);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.cursorId()).isEqualTo(idOf(2));
  }

  @Test
  void afterSeq_usesTheAscendingQuery() {
    aliceCanRead();
    when(messagePort.findAfterSeq(CHANNEL, 2L, 3)).thenReturn(seqs(3, 4, 5));

    MessageList page = service.getMessages(history(null, null, 2L, 2));

    assertThat(page.items()).extracting(MessageItem::getSeq).containsExactly(3L, 4L);
    assertThat(page.hasMore()).isTrue();
  }

  @Test
  void noCursor_readsTheNewestBySeq() {
    aliceCanRead();
    when(messagePort.findBeforeSeq(CHANNEL, null, 21)).thenReturn(seqs(2, 1));

    MessageList page = service.getMessages(history(null, null, null, 20));

    assertThat(page.items()).extracting(MessageItem::getSeq).containsExactly(2L, 1L);
    assertThat(page.hasMore()).isFalse();
  }

  @Test
  void exactlyLimitLeft_hasMoreFalse_andNullCursorId() {
    aliceCanRead();
    when(messagePort.findBeforeSeq(CHANNEL, 3L, 3)).thenReturn(seqs(2, 1));

    MessageList page = service.getMessages(history(null, 3L, null, 2));

    assertThat(page.items()).hasSize(2);
    assertThat(page.hasMore()).isFalse();
    assertThat(page.cursorId()).isNull();
  }

  @Test
  void cursorId_usesTheLegacyIdQuery() {
    aliceCanRead();
    when(messagePort.findMessages(CHANNEL, idOf(5), 3)).thenReturn(seqs(4, 3, 2));

    MessageList page = service.getMessages(history(idOf(5), null, null, 2));

    assertThat(page.items()).extracting(MessageItem::getSeq).containsExactly(4L, 3L);
    assertThat(page.cursorId()).isEqualTo(idOf(3));
  }

  @ParameterizedTest
  @CsvSource({"100, 2, ", "100, , 2", ", 9, 2", "100, 9, 2"})
  void twoCursors_throws_andReadsNothing(Long cursorId, Long beforeSeq, Long afterSeq) {
    assertThatThrownBy(() -> service.getMessages(history(cursorId, beforeSeq, afterSeq, 20)))
        .isInstanceOf(InvalidHistoryCursorException.class);
    verifyNoInteractions(messagePort);
  }

  @Test
  void nonMember_readsNothing() {
    when(channelPort.existById(CHANNEL)).thenReturn(true);
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);

    assertThatThrownBy(() -> service.getMessages(history(null, null, null, 20)))
        .isInstanceOf(MemberNotFoundException.class);
    verifyNoInteractions(messagePort);
  }

  @Test
  void missingChannel_readsNothing() {
    when(channelPort.existById(CHANNEL)).thenReturn(false);

    assertThatThrownBy(() -> service.getMessages(history(null, null, null, 20)))
        .isInstanceOf(ChannelNotFoundException.class);
    verifyNoInteractions(messagePort);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private void aliceCanRead() {
    when(channelPort.existById(CHANNEL)).thenReturn(true);
    aliceIsMember();
  }

  private static GetMessageCommand history(
      Long cursorId, Long beforeSeq, Long afterSeq, int limit) {
    return new GetMessageCommand(ALICE, CHANNEL, cursorId, beforeSeq, afterSeq, limit);
  }

  /** Ids follow seq here; the id/seq mismatch is covered by MessageAdapterIT. */
  private static long idOf(long seq) {
    return 100 + seq;
  }

  private static List<MessageItem> seqs(long... seqs) {
    return Arrays.stream(seqs)
        .mapToObj(seq -> MessageItem.builder().id(idOf(seq)).seq(seq).build())
        .toList();
  }

  private void aliceIsMember() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(true);
  }

  private static CreateMessageCommand text(long channelId) {
    return new CreateMessageCommand("hello", ContentType.TEXT, channelId, ALICE, KEY);
  }

  /** The message Alice already stored under {@link #KEY}. */
  private static MessageKey existing(long channelId) {
    return new MessageKey(100L, channelId, 42L, ORIGINAL_TIME);
  }

  /** Runs callbacks inline; counts opened and rolled-back transactions. */
  private static final class CountingTransactions implements TransactionOperations {
    int started;
    int rolledBack;

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      started++;
      TransactionStatus status = new SimpleTransactionStatus();
      T result = action.doInTransaction(status);
      if (status.isRollbackOnly()) {
        rolledBack++;
      }
      return result;
    }
  }
}
