package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
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
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.model.ReplyTarget;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  private static final long CHANNEL = 1L, OTHER_CHANNEL = 2L, ALICE = 10L;
  private static final UUID KEY = UUID.fromString("7f1c2a9e-0000-4000-8000-000000000001");
  private static final Instant ORIGINAL_TIME = Instant.parse("2026-11-01T10:00:00Z");

  @Mock private MessagePort messagePort;
  @Mock private ReactionPort reactionPort;
  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private final CountingTransactions transactions = new CountingTransactions();
  private MessageService service;

  @BeforeEach
  void setUp() {
    service = serviceWith(new TextMessageCreationStrategy());
  }

  /** Seq assignment and idempotent send (P3-T03, ADR-0012). */
  @Nested
  class Send {

    @Test
    void firstSend_takesSeqFromChannelCounter_andSavesItWithTheKey() {
      aliceIsMember();
      freshSend(42L);

      SavedMessage saved = service.save(text(CHANNEL));

      Message stored = storedMessage();
      assertThat(stored.getSeq()).isEqualTo(42L);
      assertThat(stored.getClientMsgId()).isEqualTo(KEY);
      assertThat(stored.getChannelId()).isEqualTo(CHANNEL);
      assertThat(stored.getSenderId()).isEqualTo(ALICE);
      assertThat(saved.id()).isEqualTo(100L);
      assertThat(saved.seq()).isEqualTo(42L);
      assertThat(saved.createdTime()).isEqualTo(stored.getCreatedTime()).isNotNull();
      assertThat(saved.duplicate()).isFalse();
    }

    @Test
    void returnsSameSeqOnRetriedClientMsgId_withoutBumpOrInsert() {
      aliceIsMember();
      alreadyStored(CHANNEL);

      SavedMessage saved = service.save(text(CHANNEL));

      assertThat(saved).isEqualTo(new SavedMessage(100L, 42L, ORIGINAL_TIME, true));
      assertNothingBumped();
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
      aliceIsNotMember();

      CreateMessageCommand send = text(CHANNEL);

      assertThatThrownBy(() -> service.save(send)).isInstanceOf(MemberNotFoundException.class);
      verify(channelPort, never()).getNextSequence(anyLong());
      verifyNoInteractions(messagePort);
    }

    @Test
    void unknownChannel_getsTheSameAnswerAsNonMember() {
      // No member row can exist without its channel (FK), so this also covers a missing channel.
      when(memberPort.existMemberOfChannelByUserId(OTHER_CHANNEL, ALICE)).thenReturn(false);

      // A distinct "channel not found" would let any user map which channel ids exist.
      CreateMessageCommand send = text(OTHER_CHANNEL);
      assertThatThrownBy(() -> service.save(send)).isInstanceOf(MemberNotFoundException.class);
      verifyNoInteractions(channelPort, messagePort);
    }

    @Test
    void keyReusedInAnotherChannel_isRejectedWithoutBump() {
      aliceIsMember();
      alreadyStored(OTHER_CHANNEL);

      CreateMessageCommand send = text(CHANNEL);

      assertThatThrownBy(() -> service.save(send)).isInstanceOf(ClientMsgIdReusedException.class);
      assertNothingBumped();
    }

    @Test
    void contentTypeWithoutStrategy_isRejectedBeforeAnyBump() {
      CreateMessageCommand send = command(ContentType.MARKDOWN, "# hi");
      assertThatThrownBy(() -> service.save(send))
          .isInstanceOf(InvalidMessageConfigException.class);
      verifyNoInteractions(messagePort, channelPort);
    }
  }

  /** A reply's parent: same channel, live; one answer for every bad case (P3-T08). */
  @Nested
  class Replies {

    private static final long PARENT = 77L;
    private static final Instant PARENT_DELETED = ORIGINAL_TIME.plusSeconds(60);

    @Test
    void send_withoutReplyTo_savesNullReplyTo_andLooksNothingUp() {
      aliceIsMember();
      freshSend(42L);

      service.save(text(CHANNEL));

      assertThat(storedMessage().getReplyToId()).isNull();
      verify(messagePort, never()).findReplyTarget(anyLong());
    }

    @Test
    void reply_toLiveParentInTheSameChannel_savesReplyToId() {
      aliceIsMember();
      freshSend(42L);
      parentIs(new ReplyTarget(CHANNEL, null));

      service.save(reply(PARENT));

      assertThat(storedMessage().getReplyToId()).isEqualTo(PARENT);
    }

    @Test
    void reply_toUnknownParent_isRejected_beforeAnyBump() {
      aliceIsMember();
      noReplay();
      when(messagePort.findReplyTarget(PARENT)).thenReturn(Optional.empty());

      assertRejectedWithoutBump(reply(PARENT));
    }

    @Test
    void reply_toParentInAnotherChannel_isRejected_beforeAnyBump() {
      // Otherwise any member could pull a preview of a channel they can't read (BOLA).
      aliceIsMember();
      noReplay();
      parentIs(new ReplyTarget(OTHER_CHANNEL, null));

      assertRejectedWithoutBump(reply(PARENT));
    }

    @Test
    void reply_toDeletedParent_isRejected_beforeAnyBump() {
      aliceIsMember();
      noReplay();
      parentIs(new ReplyTarget(CHANNEL, PARENT_DELETED));

      assertRejectedWithoutBump(reply(PARENT));
    }

    @Test
    void reply_byNonMember_isRejected_beforeTheParentIsLookedUp() {
      // Membership first: otherwise an outsider learns whether a message id exists.
      aliceIsNotMember();

      CreateMessageCommand send = reply(PARENT);

      assertThatThrownBy(() -> service.save(send)).isInstanceOf(MemberNotFoundException.class);
      verifyNoInteractions(messagePort);
    }

    @Test
    void retry_afterTheParentWasDeleted_isStillTheDuplicate_notRejected() {
      // The original reply was saved while the parent was live; the replay wins over the check.
      aliceIsMember();
      alreadyStored(CHANNEL);

      SavedMessage saved = service.save(reply(PARENT));

      assertThat(saved.duplicate()).isTrue();
      verify(messagePort, never()).findReplyTarget(anyLong());
    }

    private void parentIs(ReplyTarget target) {
      when(messagePort.findReplyTarget(PARENT)).thenReturn(Optional.of(target));
    }

    private void assertRejectedWithoutBump(CreateMessageCommand command) {
      assertThatThrownBy(() -> service.save(command))
          .isInstanceOf(InvalidMessageConfigException.class)
          .hasMessage("Invalid replyToId");
      assertNothingBumped();
    }

    private static CreateMessageCommand reply(long parentId) {
      return new CreateMessageCommand("re", ContentType.TEXT, CHANNEL, ALICE, KEY, parentId);
    }
  }

  /** Markdown and the text content rules (P3-T15). */
  @Nested
  class ContentRules {

    @BeforeEach
    void withMarkdown() {
      service =
          serviceWith(new TextMessageCreationStrategy(), new MarkdownMessageCreationStrategy());
    }

    @Test
    void markdownSend_getsTheNextSeq_andStoresTheRawSource() {
      aliceIsMember();
      freshSend(7L);

      SavedMessage saved = service.save(command(ContentType.MARKDOWN, "# hi **x**"));

      Message stored = storedMessage();
      assertThat(stored.getContentType()).isEqualTo(ContentType.MARKDOWN);
      assertThat(stored.getContent()).isEqualTo("# hi **x**");
      assertThat(stored.getSeq()).isEqualTo(7L);
      assertThat(saved.seq()).isEqualTo(7L);
    }

    @Test
    void markdownSend_byNonMember_isRejectedBeforeAnyBump() {
      aliceIsNotMember();

      CreateMessageCommand send = command(ContentType.MARKDOWN, "# hi");

      assertThatThrownBy(() -> service.save(send)).isInstanceOf(MemberNotFoundException.class);
      verify(channelPort, never()).getNextSequence(anyLong());
      verifyNoInteractions(messagePort);
    }

    @ParameterizedTest
    @MethodSource("invalidContent")
    void invalidContent_isRejectedBeforeAnyPortCall(ContentType type, String content) {
      CreateMessageCommand send = command(type, content);
      assertThatThrownBy(() -> service.save(send))
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
      aliceIsMember();
      freshSend(1L);
      String emoji = "🙂".repeat(1000); // 1000 code points, 2000 chars

      assertThat(service.save(command(type, emoji)).seq()).isEqualTo(1L);
    }

    static Stream<Arguments> invalidContent() {
      return Stream.of(ContentType.TEXT, ContentType.MARKDOWN)
          .flatMap(
              type ->
                  Stream.of(
                      Arguments.of(type, null),
                      Arguments.of(type, ""),
                      Arguments.of(type, " \n\t "),
                      Arguments.of(type, "x".repeat(1001)),
                      Arguments.of(type, "hi\0there"))); // PG varchar rejects 0x00
    }
  }

  /** History paging by seq, and the deprecated cursorId alias (P3-T05). */
  @Nested
  class History {

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
      GetMessageCommand query = history(cursorId, beforeSeq, afterSeq, 20);
      assertThatThrownBy(() -> service.getMessages(query))
          .isInstanceOf(InvalidHistoryCursorException.class);
      verifyNoInteractions(messagePort);
    }

    @Test
    void nonMember_readsNothing() {
      when(channelPort.existById(CHANNEL)).thenReturn(true);
      aliceIsNotMember();

      GetMessageCommand query = history(null, null, null, 20);

      assertThatThrownBy(() -> service.getMessages(query))
          .isInstanceOf(MemberNotFoundException.class);
      verifyNoInteractions(messagePort);
    }

    @Test
    void missingChannel_readsNothing() {
      when(channelPort.existById(CHANNEL)).thenReturn(false);

      GetMessageCommand query = history(null, null, null, 20);

      assertThatThrownBy(() -> service.getMessages(query))
          .isInstanceOf(ChannelNotFoundException.class);
      verifyNoInteractions(messagePort);
    }

    /** P3-T09: one summarize call per page, for the returned items only (not the extra row). */
    @ParameterizedTest
    @CsvSource({"CURSOR", "BEFORE_SEQ", "AFTER_SEQ"})
    void everyCursor_summarizesThePageOnce_andAttachesTheReactions(String cursor) {
      aliceCanRead();
      GetMessageCommand query =
          switch (cursor) {
            case "CURSOR" -> {
              when(messagePort.findMessages(CHANNEL, idOf(9), 3)).thenReturn(seqs(3, 2, 1));
              yield history(idOf(9), null, null, 2);
            }
            case "BEFORE_SEQ" -> {
              when(messagePort.findBeforeSeq(CHANNEL, 9L, 3)).thenReturn(seqs(3, 2, 1));
              yield history(null, 9L, null, 2);
            }
            default -> {
              when(messagePort.findAfterSeq(CHANNEL, 0L, 3)).thenReturn(seqs(1, 2, 3));
              yield history(null, null, 0L, 2);
            }
          };
      ReactionSummary thumbs = new ReactionSummary("👍", 2, true);
      Long first = idOf(cursor.equals("AFTER_SEQ") ? 1 : 3);
      when(reactionPort.summarize(any(), any())).thenReturn(Map.of(first, List.of(thumbs)));

      MessageList page = service.getMessages(query);

      ArgumentCaptor<List<Long>> ids = ArgumentCaptor.captor();
      verify(reactionPort).summarize(ids.capture(), eq(ALICE));
      assertThat(ids.getValue())
          .containsExactlyInAnyOrderElementsOf(
              page.items().stream().map(MessageItem::getId).toList());
      assertThat(page.items().getFirst().getReactions()).containsExactly(thumbs);
      assertThat(page.items().getLast().getReactions()).isEmpty();
    }

    @Test
    void tombstone_showsNoReactions_evenIfRowsRemain() {
      aliceCanRead();
      MessageItem deleted =
          MessageItem.builder().id(idOf(1)).seq(1L).deletedTime(ORIGINAL_TIME).build();
      when(messagePort.findBeforeSeq(CHANNEL, null, 21)).thenReturn(List.of(deleted));
      when(reactionPort.summarize(any(), any()))
          .thenReturn(Map.of(idOf(1), List.of(new ReactionSummary("👍", 1, false))));

      MessageList page = service.getMessages(history(null, null, null, 20));

      assertThat(page.items().getFirst().getReactions()).isEmpty();
    }

    @Test
    void nonMember_neverSummarizes() {
      when(channelPort.existById(CHANNEL)).thenReturn(true);
      aliceIsNotMember();

      GetMessageCommand query = history(null, null, null, 20);

      assertThatThrownBy(() -> service.getMessages(query))
          .isInstanceOf(MemberNotFoundException.class);
      verifyNoInteractions(reactionPort);
    }

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
  }

  // ---- shared stubs and assertions -------------------------------------------------------------

  private MessageService serviceWith(MessageCreationStrategy... strategies) {
    return new MessageService(
        List.of(strategies),
        messagePort,
        reactionPort,
        channelPort,
        new ChannelAuthorizer(channelPort, memberPort),
        transactions);
  }

  private void aliceIsMember() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(true);
  }

  private void aliceIsNotMember() {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(false);
  }

  /** No earlier send under {@link #KEY}. */
  private void noReplay() {
    when(messagePort.findByClientMsgId(ALICE, KEY)).thenReturn(Optional.empty());
  }

  /** A first send: no replay, the channel hands out {@code seq}, the insert gets id 100. */
  private void freshSend(long seq) {
    noReplay();
    when(channelPort.getNextSequence(CHANNEL)).thenReturn(Optional.of(seq));
    when(messagePort.save(any())).thenReturn(Optional.of(100L));
  }

  /** Alice already sent under {@link #KEY}, in {@code channelId}. */
  private void alreadyStored(long channelId) {
    when(messagePort.findByClientMsgId(ALICE, KEY)).thenReturn(Optional.of(existing(channelId)));
  }

  private Message storedMessage() {
    ArgumentCaptor<Message> stored = ArgumentCaptor.forClass(Message.class);
    verify(messagePort).save(stored.capture());
    return stored.getValue();
  }

  private void assertNothingBumped() {
    verify(channelPort, never()).getNextSequence(anyLong());
    verify(messagePort, never()).save(any());
  }

  private static CreateMessageCommand text(long channelId) {
    return new CreateMessageCommand("hello", ContentType.TEXT, channelId, ALICE, KEY);
  }

  private static CreateMessageCommand command(ContentType type, String content) {
    return new CreateMessageCommand(content, type, CHANNEL, ALICE, KEY);
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
