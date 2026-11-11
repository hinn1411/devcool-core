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
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.support.AbstractJpaIT;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @BeforeEach
  void seedUsersAndChannels() {
    alice = user("Alice", "https://cdn.example/alice.png");
    channelA = channel("channel-a");
    channelB = channel("channel-b");
  }

  @Test
  void nullCursor_returnsNewestSizeMessages_descending() {
    Long m1 = text(channelA, "m1");
    Long m2 = text(channelA, "m2");
    Long m3 = text(channelA, "m3");
    Long m4 = text(channelA, "m4");
    Long m5 = text(channelA, "m5");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 3);

    assertThat(page).extracting(MessageItem::getId).containsExactly(m5, m4, m3);
  }

  @Test
  void cursor_returnsOnlyOlderMessages_excludingTheCursor() {
    Long m1 = text(channelA, "m1");
    Long m2 = text(channelA, "m2");
    Long m3 = text(channelA, "m3");
    Long m4 = text(channelA, "m4");
    Long m5 = text(channelA, "m5");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), m4, 2);

    assertThat(page).extracting(MessageItem::getId).containsExactly(m3, m2).doesNotContain(m4);
  }

  @Test
  void cursorAtOldestMessage_returnsEmpty() {
    Long m1 = text(channelA, "m1");
    Long m2 = text(channelA, "m2");
    Long m3 = text(channelA, "m3");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), m1, 3);

    assertThat(page).isEmpty();
  }

  @Test
  void emptyChannel_nullCursor_returnsEmpty() {
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 3);

    assertThat(page).isEmpty();
  }

  @Test
  void sizeEqualToRemainingRows_returnsAllOfThem() {
    Long m1 = text(channelA, "m1");
    Long m2 = text(channelA, "m2");
    Long m3 = text(channelA, "m3");
    Long m4 = text(channelA, "m4");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), m4, 3);

    assertThat(page).hasSize(3);
    assertThat(page).extracting(MessageItem::getId).containsExactly(m3, m2, m1);
  }

  @Test
  void softDeletedMessage_isATombstone_inTheCursorPage() {
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

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 5);

    assertThat(page).extracting(MessageItem::getId).containsExactly(a3, a2, a1).doesNotContain(b1);
  }

  @Test
  void textMessage_mapsEveryField() {
    Long id = text(channelA, "hello");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 1);

    assertThat(page)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getId()).isEqualTo(id);
              assertThat(e.getContentType()).isEqualTo(ContentType.TEXT);
              assertThat(e.getContent()).isEqualTo("hello");
              assertThat(e.getCreatedTime()).isEqualTo(T0.plusSeconds(1));
              assertThat(e.getSenderAvatar()).isEqualTo(alice.getAvatar());
              assertThat(e.getUserId()).isEqualTo(alice.getId());
              assertThat(e.getSenderName()).isEqualTo(alice.getName());
            });
  }

  @Test
  void imageMessage_contentIsTheMediaPath_andSenderIsMapped() {
    Long id = image(channelA, "channel/1/2026/10/01/abc.jpg");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 1);

    assertThat(page)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getId()).isEqualTo(id);
              assertThat(e.getContentType()).isEqualTo(ContentType.IMAGE);
              assertThat(e.getContent()).isEqualTo("channel/1/2026/10/01/abc.jpg");
              // The media join must not lose the sender join
              assertThat(e.getUserId()).isEqualTo(alice.getId());
              assertThat(e.getSenderName()).isEqualTo(alice.getName());
              assertThat(e.getSenderAvatar()).isEqualTo(alice.getAvatar());
            });
  }

  // ---- seq + idempotency key (P3-T03) ---------------------------------------------------------

  @Test
  void savedSeqAndClientMsgId_areFoundBySenderAndKey() {
    UUID key = UUID.randomUUID();
    Long id = messagePort.save(domainText(alice, channelA, 7L, key)).orElseThrow();
    resetPersistenceContext();

    assertThat(messagePort.findByClientMsgId(alice.getId(), key))
        .contains(new MessageKey(id, channelA.getId(), 7L, T0));
  }

  @Test
  void sameKeyOfAnotherSender_isNotFound() {
    UserEntity bob = user("Bob", null);
    UUID key = UUID.randomUUID();
    messagePort.save(domainText(alice, channelA, 1L, key));
    resetPersistenceContext();

    assertThat(messagePort.findByClientMsgId(bob.getId(), key)).isEmpty();
  }

  @Test
  void secondMessageWithSameSenderAndKey_returnsEmpty() {
    UUID key = UUID.randomUUID();
    messagePort.save(domainText(alice, channelA, 1L, key));

    // Another channel, so only the (user_id, client_msg_id) key can collide.
    assertThat(messagePort.save(domainText(alice, channelB, 1L, key))).isEmpty();
  }

  // ---- seq paging (P3-T05) ---------------------------------------------------------------------

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

  /** Seq and sender stay so the client can render "message deleted" in place. */
  private void assertTombstone(MessageItem item) {
    assertThat(item.getDeletedTime()).isNotNull();
    assertThat(item.getContent()).isNull();
    assertThat(item.getSeq()).isNotNull();
    assertThat(item.getUserId()).isEqualTo(alice.getId());
  }

  private static Message domainText(UserEntity sender, ChannelEntity channel, long seq, UUID key) {
    return Message.builder()
        .senderId(sender.getId())
        .channelId(channel.getId())
        .contentType(ContentType.TEXT)
        .content("hello")
        .createdTime(T0)
        .seq(seq)
        .clientMsgId(key)
        .build();
  }

  // ---- edit (P3-T06) ---------------------------------------------------------------------------

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

  // ---- delete (P3-T07) -------------------------------------------------------------------------

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

  private Instant storedDeletedTime(Long id) {
    return toInstant(
        em.getEntityManager()
            .createNativeQuery("select deleted_time from message where id = :id")
            .setParameter("id", id)
            .getSingleResult());
  }

  /** content, version, edited_time straight from Postgres (bypasses the entity mapping). */
  private List<Object> storedRow(Long id) {
    Object[] row =
        (Object[])
            em.getEntityManager()
                .createNativeQuery(
                    "select content, version, edited_time from message where id = :id")
                .setParameter("id", id)
                .getSingleResult();
    return java.util.Arrays.asList(row[0], ((Number) row[1]).intValue(), toInstant(row[2]));
  }

  private static Instant toInstant(Object value) {
    return switch (value) {
      case null -> null;
      case Instant instant -> instant;
      case java.time.OffsetDateTime odt -> odt.toInstant();
      case java.sql.Timestamp ts -> ts.toInstant();
      default -> throw new IllegalStateException("Unexpected " + value.getClass());
    };
  }

  // ---- seed helpers ----------------------------------------------------------------------------

  private int sequence;

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

  /** One second apart; ids and seqs follow insertion order. */
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

  private Long deletedText(ChannelEntity channel, String content) {
    MessageEntity message = message(channel, ContentType.TEXT, content);
    message.setDeletedTime(message.getCreatedTime().plusSeconds(30));
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
