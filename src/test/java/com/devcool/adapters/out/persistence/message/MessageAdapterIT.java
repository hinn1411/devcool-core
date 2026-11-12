package com.devcool.adapters.out.persistence.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.media.entity.MediaEntity;
import com.devcool.adapters.out.persistence.media.mapper.MediaMapperImpl;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.message.mapper.MessageMapperImpl;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.chat.model.DeletableMessage;
import com.devcool.domain.chat.model.EditableMessage;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageKey;
import com.devcool.domain.chat.model.ReplyPreview;
import com.devcool.domain.chat.model.ReplyTarget;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.support.AbstractJpaIT;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** MessagePort against real Postgres. */
@Import({MessageAdapter.class, MessageMapperImpl.class, MediaMapperImpl.class})
class MessageAdapterIT extends AbstractJpaIT {

  // Microsecond precision: Postgres stores timestamp(6), so values round-trip exactly.
  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  @Autowired MessagePort messagePort;
  @Autowired TestEntityManager em;

  private UserEntity alice;
  private ChannelEntity channelA;
  private ChannelEntity channelB;
  private int sequence;

  @BeforeEach
  void seedUsersAndChannels() {
    alice = user("Alice", "https://cdn.example/alice.png");
    channelA = channel("channel-a");
    channelB = channel("channel-b");
  }

  /** The deprecated id cursor, and the item mapping every history query shares (P3-T05). */
  @Nested
  class CursorAlias {

    @Test
    void nullCursor_returnsNewestSizeMessages_descending() {
      texts(channelA, "m1", "m2");
      Long m3 = text(channelA, "m3");
      Long m4 = text(channelA, "m4");
      Long m5 = text(channelA, "m5");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), null, 3))
          .extracting(MessageItem::getId)
          .containsExactly(m5, m4, m3);
    }

    @Test
    void cursor_returnsOnlyOlderMessages_excludingTheCursor() {
      Long m1 = text(channelA, "m1");
      Long m2 = text(channelA, "m2");
      Long m3 = text(channelA, "m3");
      Long m4 = text(channelA, "m4");
      text(channelA, "m5");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), m4, 2))
          .extracting(MessageItem::getId)
          .containsExactly(m3, m2)
          .doesNotContain(m4, m1);
    }

    @Test
    void cursorAtOldestMessage_returnsEmpty() {
      Long m1 = text(channelA, "m1");
      texts(channelA, "m2", "m3");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), m1, 3)).isEmpty();
    }

    @Test
    void emptyChannel_nullCursor_returnsEmpty() {
      assertThat(messagePort.findMessages(channelA.getId(), null, 3)).isEmpty();
    }

    @Test
    void sizeEqualToRemainingRows_returnsAllOfThem() {
      Long m1 = text(channelA, "m1");
      Long m2 = text(channelA, "m2");
      Long m3 = text(channelA, "m3");
      Long m4 = text(channelA, "m4");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), m4, 3))
          .extracting(MessageItem::getId)
          .containsExactly(m3, m2, m1);
    }

    @Test
    void softDeletedMessage_isATombstone() {
      Long m1 = text(channelA, "m1");
      Long m2 = deletedText(channelA, "m2 (deleted)");
      Long m3 = text(channelA, "m3");
      resetPersistenceContext();

      List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 3);

      assertThat(page).extracting(MessageItem::getId).containsExactly(m3, m2, m1);
      assertTombstone(page.get(1));
    }

    @Test
    void messageOfAnotherChannel_isExcluded_evenBetweenOwnIds() {
      Long a1 = text(channelA, "a1");
      Long a2 = text(channelA, "a2");
      Long b1 = text(channelB, "b1");
      Long a3 = text(channelA, "a3");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), null, 5))
          .extracting(MessageItem::getId)
          .containsExactly(a3, a2, a1)
          .doesNotContain(b1);
    }

    @Test
    void textMessage_mapsEveryField() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), null, 1))
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.getId()).isEqualTo(id);
                assertThat(e.getContentType()).isEqualTo(ContentType.TEXT);
                assertThat(e.getContent()).isEqualTo("hello");
                assertThat(e.getCreatedTime()).isEqualTo(T0.plusSeconds(1));
                assertSentByAlice(e);
              });
    }

    @Test
    void imageMessage_contentIsTheMediaPath_andSenderIsMapped() {
      Long id = image(channelA, "channel/1/2026/10/01/abc.jpg");
      resetPersistenceContext();

      assertThat(messagePort.findMessages(channelA.getId(), null, 1))
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e.getId()).isEqualTo(id);
                assertThat(e.getContentType()).isEqualTo(ContentType.IMAGE);
                assertThat(e.getContent()).isEqualTo("channel/1/2026/10/01/abc.jpg");
                assertSentByAlice(e); // the media join must not lose the sender join
              });
    }
  }

  /** The sender's idempotency key (P3-T03). */
  @Nested
  class IdempotencyKey {

    @Test
    void savedSeqAndClientMsgId_areFoundBySenderAndKey() {
      UUID key = UUID.randomUUID();
      Long id = messagePort.save(domainText(channelA, 7L, key)).orElseThrow();
      resetPersistenceContext();

      assertThat(messagePort.findByClientMsgId(alice.getId(), key))
          .contains(new MessageKey(id, channelA.getId(), 7L, T0));
    }

    @Test
    void sameKeyOfAnotherSender_isNotFound() {
      UserEntity bob = user("Bob", null);
      UUID key = UUID.randomUUID();
      messagePort.save(domainText(channelA, 1L, key));
      resetPersistenceContext();

      assertThat(messagePort.findByClientMsgId(bob.getId(), key)).isEmpty();
    }

    @Test
    void secondMessageWithSameSenderAndKey_returnsEmpty() {
      UUID key = UUID.randomUUID();
      messagePort.save(domainText(channelA, 1L, key));

      // Another channel, so only the (user_id, client_msg_id) key can collide.
      assertThat(messagePort.save(domainText(channelB, 1L, key))).isEmpty();
    }
  }

  /** beforeSeq / afterSeq range scans (P3-T05) and their tombstones (P3-T07). */
  @Nested
  class SeqPaging {

    @Test
    void beforeSeq_returnsOlderBySeqDescending_excludingTheBound() {
      withSeqs(channelA, 1, 2, 3, 4, 5);
      resetPersistenceContext();

      assertThat(messagePort.findBeforeSeq(channelA.getId(), 4L, 2))
          .extracting(MessageItem::getSeq)
          .containsExactly(3L, 2L);
    }

    @Test
    void afterSeq_returnsNewerBySeqAscending_excludingTheBound() {
      withSeqs(channelA, 1, 2, 3, 4, 5);
      resetPersistenceContext();

      assertThat(messagePort.findAfterSeq(channelA.getId(), 2L, 2))
          .extracting(MessageItem::getSeq)
          .containsExactly(3L, 4L);
    }

    @Test
    void nullBeforeSeq_returnsNewestBySeq() {
      withSeqs(channelA, 1, 2, 3, 4, 5);
      resetPersistenceContext();

      assertThat(messagePort.findBeforeSeq(channelA.getId(), null, 3))
          .extracting(MessageItem::getSeq)
          .containsExactly(5L, 4L, 3L);
    }

    @Test
    void seqPaging_followsSeqEvenWhenIdOrderDiffers() {
      // Like V5's renumbered rows: the oldest id holds the highest seq.
      withSeqs(channelA, 3, 1, 2);
      resetPersistenceContext();

      assertThat(messagePort.findBeforeSeq(channelA.getId(), null, 3))
          .extracting(MessageItem::getSeq)
          .containsExactly(3L, 2L, 1L);
      assertThat(messagePort.findAfterSeq(channelA.getId(), 0L, 3))
          .extracting(MessageItem::getSeq)
          .containsExactly(1L, 2L, 3L);
    }

    @Test
    void seqQueries_keepDeletedAsTombstones_andExcludeOtherChannels() {
      withSeqs(channelA, 1);
      MessageEntity deleted = message(channelA, ContentType.TEXT, "deleted");
      deleted.setSeq(2L);
      deleted.setDeletedTime(deleted.getCreatedTime().plusSeconds(30));
      em.persist(deleted);
      withSeqs(channelA, 3);
      withSeqs(channelB, 1, 2, 4);
      resetPersistenceContext();

      List<MessageItem> before = messagePort.findBeforeSeq(channelA.getId(), null, 5);
      List<MessageItem> after = messagePort.findAfterSeq(channelA.getId(), 0L, 5);

      // ADR-0012: a deleted message keeps its seq, so the gap is explained, not silent.
      assertThat(before).extracting(MessageItem::getSeq).containsExactly(3L, 2L, 1L);
      assertThat(after).extracting(MessageItem::getSeq).containsExactly(1L, 2L, 3L);
      assertTombstone(before.get(1));
      assertTombstone(after.get(1));
    }

    @Test
    void deletedImage_tombstone_dropsTheMediaPath() {
      Long id = image(channelA, "channel/1/2026/10/01/abc.jpg");
      resetPersistenceContext();
      messagePort.markDeleted(id, T0.plusSeconds(3600));

      assertTombstone(messagePort.findBeforeSeq(channelA.getId(), null, 1).getFirst());
    }
  }

  /** Edit: load, compare-and-set on version (P3-T06). */
  @Nested
  class Edit {

    @Test
    void findForEdit_returnsTheEditableFields() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();

      assertThat(messagePort.findForEdit(id))
          .contains(
              new EditableMessage(
                  id,
                  channelA.getId(),
                  (long) sequence,
                  alice.getId(),
                  ContentType.TEXT,
                  "hello",
                  0,
                  T0.plusSeconds(sequence),
                  null));
    }

    @Test
    void findForEdit_excludesDeleted() {
      Long id = deletedText(channelA, "gone");
      resetPersistenceContext();

      assertThat(messagePort.findForEdit(id)).isEmpty();
    }

    @Test
    void updateContent_onCurrentVersion_bumpsAndSetsEditedTime() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();
      Instant editedAt = T0.plusSeconds(3600);

      assertThat(messagePort.updateContent(id, 0, "hello again", editedAt)).isTrue();

      assertThat(storedRow(id)).containsExactly("hello again", 1, editedAt);
    }

    @Test
    void updateContent_staleVersion_leavesRowUnchanged() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();

      assertThat(messagePort.updateContent(id, 1, "lost update", T0.plusSeconds(3600))).isFalse();

      assertThat(storedRow(id)).containsExactly("hello", 0, null);
    }

    @Test
    void updateContent_onDeletedMessage_matchesNothing() {
      Long id = deletedText(channelA, "gone");
      resetPersistenceContext();

      assertThat(messagePort.updateContent(id, 0, "back", T0.plusSeconds(3600))).isFalse();
    }

    @Test
    void historyItems_carryVersionAndEditedTime() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();
      Instant editedAt = T0.plusSeconds(3600);
      messagePort.updateContent(id, 0, "edited", editedAt);

      assertThat(messagePort.findBeforeSeq(channelA.getId(), null, 1))
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.getVersion()).isEqualTo(1);
                assertThat(item.getEditedTime()).isEqualTo(editedAt);
              });
    }
  }

  /** Soft delete: load incl. deleted, first timestamp wins (P3-T07). */
  @Nested
  class Delete {

    @Test
    void findForDelete_includesDeletedMessages() {
      Long live = text(channelA, "live");
      Long gone = deletedText(channelA, "gone");
      resetPersistenceContext();

      assertThat(messagePort.findForDelete(live))
          .contains(new DeletableMessage(live, channelA.getId(), alice.getId(), null));
      assertThat(messagePort.findForDelete(gone))
          .hasValueSatisfying(m -> assertThat(m.deletedTime()).isNotNull());
    }

    @Test
    void findForDelete_unknownId_isEmpty() {
      assertThat(messagePort.findForDelete(-1L)).isEmpty();
    }

    @Test
    void markDeleted_setsDeletedTime_andKeepsTheRow() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();
      Instant at = T0.plusSeconds(3600);

      assertThat(messagePort.markDeleted(id, at)).isTrue();

      assertThat(storedDeletedTime(id)).isEqualTo(at);
      assertThat(storedRow(id).getFirst()).isEqualTo("hello");
    }

    @Test
    void markDeleted_secondCall_matchesNothing_andKeepsTheFirstTimestamp() {
      Long id = text(channelA, "hello");
      resetPersistenceContext();
      Instant first = T0.plusSeconds(3600);
      messagePort.markDeleted(id, first);

      assertThat(messagePort.markDeleted(id, first.plusSeconds(60))).isFalse();

      assertThat(storedDeletedTime(id)).isEqualTo(first);
    }
  }

  /** reply_to_id, the parent lookup and the preview in every history query (P3-T08). */
  @Nested
  class Replies {

    @Test
    void save_storesReplyToId() {
      Long parent = text(channelA, "question");
      Message reply = domainText(channelA, 50L, UUID.randomUUID());
      reply.setReplyToId(parent);

      Long id = messagePort.save(reply).orElseThrow();
      resetPersistenceContext();

      assertThat(storedReplyToId(id)).isEqualTo(parent);
    }

    @Test
    void findReplyTarget_returnsChannelAndDeletedTime_evenIfDeleted() {
      Long live = text(channelA, "live");
      Long gone = deletedText(channelB, "gone");
      resetPersistenceContext();

      assertThat(messagePort.findReplyTarget(live))
          .contains(new ReplyTarget(channelA.getId(), null));
      assertThat(messagePort.findReplyTarget(gone))
          .hasValueSatisfying(
              target -> {
                assertThat(target.channelId()).isEqualTo(channelB.getId());
                assertThat(target.deletedTime()).isNotNull();
              });
    }

    @Test
    void findReplyTarget_unknownId_isEmpty() {
      assertThat(messagePort.findReplyTarget(-1L)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(HistoryQuery.class)
    void plainMessage_hasNoReplyTo(HistoryQuery query) {
      text(channelA, "hello");
      resetPersistenceContext();

      assertThat(page(query)).singleElement().extracting(MessageItem::getReplyTo).isNull();
    }

    @ParameterizedTest
    @EnumSource(HistoryQuery.class)
    void reply_carriesThePreviewOfItsParent(HistoryQuery query) {
      Long parent = text(channelA, "question");
      Long parentSeq = seqOf(parent);
      Long reply = reply(channelA, parent, "answer");
      resetPersistenceContext();

      assertThat(replyToOf(page(query), reply))
          .isEqualTo(
              new ReplyPreview(
                  parent, parentSeq, alice.getId(), "Alice", ContentType.TEXT, "question", false));
    }

    @Test
    void replyToLongParent_previewIsCutTo100Chars() {
      Long parent = text(channelA, "x".repeat(150));
      Long reply = reply(channelA, parent, "answer");
      resetPersistenceContext();

      assertThat(replyToOf(page(HistoryQuery.BEFORE_SEQ), reply))
          .extracting(ReplyPreview::preview)
          .isEqualTo("x".repeat(100));
    }

    @Test
    void replyToImage_hasNoPreviewText_butKeepsTheContentType() {
      Long parent = image(channelA, "channels/1/cat.png");
      Long reply = reply(channelA, parent, "nice cat");
      resetPersistenceContext();

      assertThat(replyToOf(page(HistoryQuery.BEFORE_SEQ), reply))
          .isNotNull()
          .extracting(ReplyPreview::contentType, ReplyPreview::preview, ReplyPreview::deleted)
          .containsExactly(ContentType.IMAGE, null, false);
    }

    @ParameterizedTest
    @EnumSource(HistoryQuery.class)
    void parentDeletedAfterTheReply_previewIsATombstone(HistoryQuery query) {
      Long parent = text(channelA, "oops");
      Long parentSeq = seqOf(parent);
      Long reply = reply(channelA, parent, "answer");
      resetPersistenceContext();
      messagePort.markDeleted(parent, T0.plusSeconds(3600));

      assertThat(replyToOf(page(query), reply))
          .isNotNull()
          .extracting(
              ReplyPreview::id, ReplyPreview::seq, ReplyPreview::deleted, ReplyPreview::preview)
          .containsExactly(parent, parentSeq, true, null);
    }

    @ParameterizedTest
    @EnumSource(HistoryQuery.class)
    void deletedReply_isATombstone_thatQuotesNothing(HistoryQuery query) {
      // The tombstone rule: content and media go, and so does the quote of the parent.
      Long parent = text(channelA, "question");
      Long reply = reply(channelA, parent, "answer");
      resetPersistenceContext();
      messagePort.markDeleted(reply, T0.plusSeconds(3600));

      MessageItem tombstone = itemOf(page(query), reply);
      assertTombstone(tombstone);
      assertThat(tombstone.getReplyTo()).isNull();
    }

    @ParameterizedTest
    @EnumSource(HistoryQuery.class)
    void pageOfReplies_isOneSqlStatement(HistoryQuery query) {
      // Parents with and without media: the inverse @OneToOne media can't be lazy, so a parent
      // loaded without a join fetch costs one extra SELECT each (N+1).
      Long p1 = text(channelA, "one");
      Long p2 = image(channelA, "channels/1/two.png");
      Long p3 = text(channelA, "three");
      List<Long> replies =
          List.of(reply(channelA, p1, "r1"), reply(channelA, p2, "r2"), reply(channelA, p3, "r3"));
      resetPersistenceContext();
      Statistics stats = statistics();

      List<MessageItem> page = page(query);

      assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
      assertThat(replies).hasSize(3).allSatisfy(id -> assertThat(replyToOf(page, id)).isNotNull());
    }
  }

  // ---- history queries -------------------------------------------------------------------------

  /** The three history reads; each must map items the same way. */
  enum HistoryQuery {
    CURSOR,
    BEFORE_SEQ,
    AFTER_SEQ
  }

  private List<MessageItem> page(HistoryQuery query) {
    Long channel = channelA.getId();
    return switch (query) {
      case CURSOR -> messagePort.findMessages(channel, null, 20);
      case BEFORE_SEQ -> messagePort.findBeforeSeq(channel, null, 20);
      case AFTER_SEQ -> messagePort.findAfterSeq(channel, 0L, 20);
    };
  }

  private static MessageItem itemOf(List<MessageItem> page, Long id) {
    return page.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
  }

  private static ReplyPreview replyToOf(List<MessageItem> page, Long id) {
    return itemOf(page, id).getReplyTo();
  }

  // ---- assertions ------------------------------------------------------------------------------

  /** Seq and sender stay so the client can render "message deleted" in place. */
  private void assertTombstone(MessageItem item) {
    assertThat(item.getDeletedTime()).isNotNull();
    assertThat(item.getContent()).isNull();
    assertThat(item.getSeq()).isNotNull();
    assertThat(item.getUserId()).isEqualTo(alice.getId());
  }

  private void assertSentByAlice(MessageItem item) {
    assertThat(item.getUserId()).isEqualTo(alice.getId());
    assertThat(item.getSenderName()).isEqualTo(alice.getName());
    assertThat(item.getSenderAvatar()).isEqualTo(alice.getAvatar());
  }

  /** Enabled and reset now: counts only the statements that follow. */
  private Statistics statistics() {
    Statistics stats =
        em.getEntityManager()
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    return stats;
  }

  // ---- raw reads (bypass the entity mapping) ---------------------------------------------------

  private Object storedColumn(String column, Long id) {
    return em.getEntityManager()
        .createNativeQuery("select " + column + " from message where id = :id")
        .setParameter("id", id)
        .getSingleResult();
  }

  private Instant storedDeletedTime(Long id) {
    return toInstant(storedColumn("deleted_time", id));
  }

  private Long storedReplyToId(Long id) {
    return storedColumn("reply_to_id", id) instanceof Number n ? n.longValue() : null;
  }

  /** content, version, edited_time straight from Postgres. */
  private List<Object> storedRow(Long id) {
    Object[] row = (Object[]) storedColumn("content, version, edited_time", id);
    return Arrays.asList(row[0], ((Number) row[1]).intValue(), toInstant(row[2]));
  }

  private static Instant toInstant(Object value) {
    return switch (value) {
      case null -> null;
      case Instant instant -> instant;
      case OffsetDateTime odt -> odt.toInstant();
      case Timestamp ts -> ts.toInstant();
      default -> throw new IllegalStateException("Unexpected " + value.getClass());
    };
  }

  private Long seqOf(Long id) {
    return em.find(MessageEntity.class, id).getSeq();
  }

  // ---- seed helpers ----------------------------------------------------------------------------

  /** Sends the queued INSERTs, then empties the cache so the query really reads Postgres. */
  private void resetPersistenceContext() {
    em.flush();
    em.clear();
  }

  private UserEntity user(String name, String avatar) {
    UserEntity user = new UserEntity();
    String unique = UUID.randomUUID().toString().substring(0, 8);
    user.setUsername("u-" + unique);
    user.setEmail(unique + "@test.dev");
    user.setPassword("not-used");
    user.setName(name);
    user.setAvatar(avatar);
    user.setRole(Role.USER);
    user.setStatus(UserStatus.ACTIVE);
    return em.persist(user);
  }

  private ChannelEntity channel(String name) {
    ChannelEntity channel = new ChannelEntity();
    channel.setName(name);
    channel.setBoundaryType(BoundaryType.PUBLIC);
    channel.setChannelType(ChannelType.LOUNGE);
    channel.setTotalOfMembers(1);
    channel.setCreator(alice);
    return em.persist(channel);
  }

  /** Alice's message, one second apart; ids and seqs follow insertion order. */
  private MessageEntity message(ChannelEntity channel, ContentType type, String content) {
    MessageEntity message = new MessageEntity();
    message.setUser(alice);
    message.setChannel(channel);
    message.setContentType(type);
    message.setContent(content);
    message.setSeq((long) ++sequence);
    message.setCreatedTime(T0.plusSeconds(sequence));
    return message;
  }

  /** A not-yet-stored domain message from Alice, as the service hands it to {@code save}. */
  private Message domainText(ChannelEntity channel, long seq, UUID key) {
    return Message.builder()
        .senderId(alice.getId())
        .channelId(channel.getId())
        .contentType(ContentType.TEXT)
        .content("hello")
        .createdTime(T0)
        .seq(seq)
        .clientMsgId(key)
        .build();
  }

  /** Persists one text message per seq, in the given order (so ids follow argument order). */
  private void withSeqs(ChannelEntity channel, long... seqs) {
    for (long seq : seqs) {
      MessageEntity message = message(channel, ContentType.TEXT, "seq " + seq);
      message.setSeq(seq);
      em.persist(message);
    }
  }

  private Long text(ChannelEntity channel, String content) {
    return em.persist(message(channel, ContentType.TEXT, content)).getId();
  }

  private void texts(ChannelEntity channel, String... contents) {
    for (String content : contents) {
      text(channel, content);
    }
  }

  private Long deletedText(ChannelEntity channel, String content) {
    MessageEntity message = message(channel, ContentType.TEXT, content);
    message.setDeletedTime(message.getCreatedTime().plusSeconds(30));
    return em.persist(message).getId();
  }

  private Long reply(ChannelEntity channel, Long parentId, String content) {
    MessageEntity message = message(channel, ContentType.TEXT, content);
    message.setReplyTo(em.getEntityManager().getReference(MessageEntity.class, parentId));
    return em.persist(message).getId();
  }

  private Long image(ChannelEntity channel, String path) {
    MessageEntity message = message(channel, ContentType.IMAGE, null);
    MediaEntity media = new MediaEntity();
    media.setPath(path);
    media.setCreatedTime(message.getCreatedTime());
    media.setMessage(message);
    message.setMedia(media);
    return em.persist(message).getId();
  }
}
