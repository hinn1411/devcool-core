package com.devcool.adapters.out.persistence.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.channel.mapper.ChannelMapperImpl;
import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.adapters.out.persistence.member.mapper.MemberMapperImpl;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.channel.model.ChannelListItem;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.ReplyPreview;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.support.AbstractJpaIT;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Channel list read state on real Postgres (P3-T10 loop 2): unread count + last message. */
@Import({ChannelAdapter.class, ChannelMapperImpl.class, MemberMapperImpl.class})
class ChannelAdapterIT extends AbstractJpaIT {

  private static final Instant T0 = Instant.parse("2026-11-13T10:00:00Z");

  @Autowired ChannelPort channels;
  @Autowired TestEntityManager em;

  private UserEntity alice;
  private UserEntity bob;

  @BeforeEach
  void seed() {
    alice = user("Alice");
    bob = user("Bob");
  }

  @Test
  void unreadCount_isLastSeqMinusLastReadSeq() {
    ChannelEntity channel = channelOf(alice);
    message(channel, bob, 7, ContentType.TEXT, "hi");
    setLastSeq(channel, 7);
    setLastReadSeq(alice, channel, 3);

    assertThat(itemFor(channel).unreadCount()).isEqualTo(4);
  }

  @Test
  void lastMessage_isTheNewestMessage_truncatedTo100CodePoints() {
    ChannelEntity channel = channelOf(alice);
    message(channel, alice, 1, ContentType.TEXT, "older");
    Long newest = message(channel, bob, 2, ContentType.TEXT, "x".repeat(150));
    setLastSeq(channel, 2);

    assertThat(itemFor(channel).lastMessage())
        .isEqualTo(
            new ReplyPreview(
                newest, 2L, bob.getId(), "Bob", ContentType.TEXT, "x".repeat(100), false));
  }

  @Test
  void deletedLastMessage_isATombstone_andStillCountsAsUnread() {
    ChannelEntity channel = channelOf(alice);
    Long deleted = message(channel, bob, 1, ContentType.TEXT, "oops");
    softDelete(deleted);
    setLastSeq(channel, 1);

    ChannelListItem item = itemFor(channel);

    assertThat(item.unreadCount()).isEqualTo(1);
    assertThat(item.lastMessage().deleted()).isTrue();
    assertThat(item.lastMessage().preview()).isNull();
  }

  @Test
  void mediaLastMessage_hasNoText() {
    ChannelEntity channel = channelOf(alice);
    message(channel, bob, 1, ContentType.IMAGE, "media/secret-key.png");
    setLastSeq(channel, 1);

    ReplyPreview last = itemFor(channel).lastMessage();

    assertThat(last.contentType()).isEqualTo(ContentType.IMAGE);
    assertThat(last.preview()).isNull();
  }

  @Test
  void emptyChannel_hasNoLastMessage_andZeroUnread() {
    ChannelEntity channel = channelOf(alice);

    ChannelListItem item = itemFor(channel);

    assertThat(item.lastMessage()).isNull();
    assertThat(item.unreadCount()).isZero();
  }

  @Test
  void aPageOfChannels_isOneStatement() {
    for (int i = 0; i < 3; i++) {
      ChannelEntity channel = channelOf(alice);
      message(channel, bob, 1, ContentType.TEXT, "m" + i);
      setLastSeq(channel, 1);
    }
    resetPersistenceContext();
    Statistics stats = statistics();

    ChannelListPage page = channels.loadChannels(alice.getId(), null, 20);

    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    assertThat(page.items())
        .hasSize(3)
        .allSatisfy(item -> assertThat(item.lastMessage()).isNotNull());
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private ChannelListItem itemFor(ChannelEntity channel) {
    resetPersistenceContext();
    return channels.loadChannels(alice.getId(), null, 20).items().stream()
        .filter(item -> item.id().equals(channel.getId()))
        .findFirst()
        .orElseThrow();
  }

  private void resetPersistenceContext() {
    em.flush();
    em.clear();
  }

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

  private void setLastSeq(ChannelEntity channel, long lastSeq) {
    native_("update channel set last_seq = ?1 where id = ?2", lastSeq, channel.getId());
  }

  private void setLastReadSeq(UserEntity user, ChannelEntity channel, long seq) {
    native_(
        "update member set last_read_seq = ?1 where user_id = ?2 and channel_id = ?3",
        seq,
        user.getId(),
        channel.getId());
  }

  private void softDelete(Long messageId) {
    native_("update message set deleted_time = now() where id = ?1", messageId);
  }

  private void native_(String sql, Object... params) {
    em.flush();
    var query = em.getEntityManager().createNativeQuery(sql);
    for (int i = 0; i < params.length; i++) {
      query.setParameter(i + 1, params[i]);
    }
    query.executeUpdate();
  }

  private UserEntity user(String name) {
    UserEntity user = new UserEntity();
    String unique = UUID.randomUUID().toString().substring(0, 8);
    user.setUsername("u-" + unique);
    user.setEmail(unique + "@test.dev");
    user.setName(name);
    user.setRole(Role.USER);
    user.setStatus(UserStatus.ACTIVE);
    return em.persist(user);
  }

  private ChannelEntity channelOf(UserEntity member) {
    ChannelEntity channel = new ChannelEntity();
    channel.setName("list-" + UUID.randomUUID().toString().substring(0, 8));
    channel.setBoundaryType(BoundaryType.PUBLIC);
    channel.setChannelType(ChannelType.LOUNGE);
    channel.setTotalOfMembers(1);
    channel.setCreator(member);
    em.persist(channel);
    em.persist(
        MemberEntity.builder()
            .user(member)
            .channel(channel)
            .role(MemberType.CREATOR)
            .joinedTime(T0)
            .build());
    return channel;
  }

  private Long message(
      ChannelEntity channel, UserEntity sender, long seq, ContentType type, String content) {
    MessageEntity message = new MessageEntity();
    message.setUser(sender);
    message.setChannel(channel);
    message.setContentType(type);
    message.setContent(content);
    message.setSeq(seq);
    message.setCreatedTime(T0.plusSeconds(seq));
    return em.persistAndFlush(message).getId();
  }
}
