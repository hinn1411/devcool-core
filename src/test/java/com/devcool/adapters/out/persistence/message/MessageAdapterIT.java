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
import com.devcool.domain.chat.model.MessageItem;
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

/** MessagePort.findMessages against real Postgres: filtering, order, cursor and mapping. */
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
    Integer m1 = text(channelA, "m1");
    Integer m2 = text(channelA, "m2");
    Integer m3 = text(channelA, "m3");
    Integer m4 = text(channelA, "m4");
    Integer m5 = text(channelA, "m5");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 3);

    assertThat(page).extracting(MessageItem::getId).containsExactly(m5, m4, m3);
  }

  @Test
  void cursor_returnsOnlyOlderMessages_excludingTheCursor() {
    Integer m1 = text(channelA, "m1");
    Integer m2 = text(channelA, "m2");
    Integer m3 = text(channelA, "m3");
    Integer m4 = text(channelA, "m4");
    Integer m5 = text(channelA, "m5");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), m4, 2);

    assertThat(page).extracting(MessageItem::getId).containsExactly(m3, m2).doesNotContain(m4);
  }

  @Test
  void cursorAtOldestMessage_returnsEmpty() {
    Integer m1 = text(channelA, "m1");
    Integer m2 = text(channelA, "m2");
    Integer m3 = text(channelA, "m3");
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
    Integer m1 = text(channelA, "m1");
    Integer m2 = text(channelA, "m2");
    Integer m3 = text(channelA, "m3");
    Integer m4 = text(channelA, "m4");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), m4, 3);

    assertThat(page).hasSize(3);
    assertThat(page).extracting(MessageItem::getId).containsExactly(m3, m2, m1);
  }

  @Test
  void softDeletedMessage_isExcluded_andPageStillFillsToSize() {
    Integer m1 = text(channelA, "m1");
    Integer m2 = text(channelA, "m2");
    Integer m3 = deletedText(channelA, "m3 (deleted)");
    Integer m4 = text(channelA, "m4");
    Integer m5 = text(channelA, "m5");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 3);

    assertThat(page).extracting(MessageItem::getId).containsExactly(m5, m4, m2).doesNotContain(m3);
  }

  @Test
  void messageOfAnotherChannel_isExcluded_evenBetweenOwnIds() {
    Integer a1 = text(channelA, "a1");
    Integer a2 = text(channelA, "a2");
    Integer b1 = text(channelB, "b1");
    Integer a3 = text(channelA, "a3");
    resetPersistenceContext();

    List<MessageItem> page = messagePort.findMessages(channelA.getId(), null, 5);

    assertThat(page).extracting(MessageItem::getId).containsExactly(a3, a2, a1).doesNotContain(b1);
  }

  @Test
  void textMessage_mapsEveryField() {
    Integer id = text(channelA, "hello");
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
    Integer id = image(channelA, "channel/1/2026/10/01/abc.jpg");
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

  /** Each message is created one second after the previous one; ids follow insertion order. */
  private MessageEntity message(ChannelEntity channel, ContentType type, String content) {
    MessageEntity message = new MessageEntity();
    message.setUser(alice);
    message.setChannel(channel);
    message.setContentType(type);
    message.setContent(content);
    message.setCreatedTime(T0.plusSeconds(++sequence));
    return message;
  }

  private Integer text(ChannelEntity channel, String content) {
    return em.persist(message(channel, ContentType.TEXT, content)).getId();
  }

  private Integer deletedText(ChannelEntity channel, String content) {
    MessageEntity message = message(channel, ContentType.TEXT, content);
    message.setDeletedTime(message.getCreatedTime().plusSeconds(30));
    return em.persist(message).getId();
  }

  private Integer image(ChannelEntity channel, String path) {
    MessageEntity message = message(channel, ContentType.IMAGE, null);
    MediaEntity media = new MediaEntity();
    media.setPath(path);
    media.setCreatedTime(message.getCreatedTime());
    media.setMessage(message);
    message.setMedia(media);
    return em.persist(message).getId();
  }
}
