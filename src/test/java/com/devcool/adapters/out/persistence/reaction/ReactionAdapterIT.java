package com.devcool.adapters.out.persistence.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.message.entity.MessageEntity;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.out.ReactionPort;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.support.AbstractJpaIT;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** ReactionPort against real Postgres (P3-T09). Seeds reactions through the port itself. */
@Import(ReactionAdapter.class)
class ReactionAdapterIT extends AbstractJpaIT {

  private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
  private static final Emoji THUMBS = new Emoji("👍");
  private static final Emoji PARTY = new Emoji("🎉");

  @Autowired ReactionPort reactions;
  @Autowired TestEntityManager em;

  private UserEntity alice;
  private UserEntity bob;
  private ChannelEntity channel;
  private int sequence;

  @BeforeEach
  void seed() {
    alice = user("Alice");
    bob = user("Bob");
    channel = channel();
  }

  @Test
  void addTwice_keepsOneRow() {
    Long message = message();

    reactions.add(message, alice.getId(), THUMBS, T0);
    reactions.add(message, alice.getId(), THUMBS, T0.plusSeconds(1));
    resetPersistenceContext();

    assertThat(reactions.summarize(List.of(message), alice.getId()))
        .containsEntry(message, List.of(new ReactionSummary("👍", 1, true)));
  }

  @Test
  void summarize_countsPerEmoji_ordersByFirstReaction_andFlagsTheViewer() {
    Long message = message();
    reactions.add(message, bob.getId(), PARTY, T0);
    reactions.add(message, alice.getId(), THUMBS, T0.plusSeconds(1));
    reactions.add(message, bob.getId(), THUMBS, T0.plusSeconds(2));
    resetPersistenceContext();

    Map<Long, List<ReactionSummary>> byMessage =
        reactions.summarize(List.of(message), alice.getId());

    assertThat(byMessage.get(message))
        .containsExactly(new ReactionSummary("🎉", 1, false), new ReactionSummary("👍", 2, true));
  }

  @Test
  void summarize_leavesOutMessagesWithoutReactions_andOtherMessages() {
    Long reacted = message();
    Long quiet = message();
    Long outsidePage = message();
    reactions.add(reacted, alice.getId(), THUMBS, T0);
    reactions.add(outsidePage, alice.getId(), THUMBS, T0);
    resetPersistenceContext();

    Map<Long, List<ReactionSummary>> byMessage =
        reactions.summarize(List.of(reacted, quiet), bob.getId());

    assertThat(byMessage).containsOnlyKeys(reacted);
    assertThat(byMessage.get(reacted)).containsExactly(new ReactionSummary("👍", 1, false));
  }

  @Test
  void remove_deletesOnlyTheCallersRow() {
    Long message = message();
    reactions.add(message, alice.getId(), THUMBS, T0);
    reactions.add(message, bob.getId(), THUMBS, T0.plusSeconds(1));

    reactions.remove(message, alice.getId(), THUMBS);
    resetPersistenceContext();

    assertThat(reactions.summarize(List.of(message), alice.getId()))
        .containsEntry(message, List.of(new ReactionSummary("👍", 1, false)));
  }

  @Test
  void remove_absentRow_isANoOp() {
    Long message = message();
    reactions.add(message, bob.getId(), PARTY, T0);

    reactions.remove(message, alice.getId(), PARTY);
    reactions.remove(message, bob.getId(), THUMBS);
    resetPersistenceContext();

    assertThat(reactions.summarize(List.of(message), bob.getId()))
        .containsEntry(message, List.of(new ReactionSummary("🎉", 1, true)));
  }

  @Test
  void summarize_aWholePage_isOneStatement() {
    List<Long> page = List.of(message(), message(), message(), message(), message());
    for (Long message : page) {
      reactions.add(message, alice.getId(), THUMBS, T0);
      reactions.add(message, bob.getId(), PARTY, T0.plusSeconds(1));
    }
    resetPersistenceContext();
    Statistics stats = statistics();

    Map<Long, List<ReactionSummary>> byMessage = reactions.summarize(page, alice.getId());

    assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    assertThat(byMessage).hasSize(page.size());
  }

  @Test
  void summarize_emptyPage_runsNoStatement() {
    Statistics stats = statistics();

    assertThat(reactions.summarize(List.of(), alice.getId())).isEmpty();
    assertThat(stats.getPrepareStatementCount()).isZero();
  }

  // ---- seed helpers ----------------------------------------------------------------------------

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

  private UserEntity user(String name) {
    UserEntity user = new UserEntity();
    String unique = UUID.randomUUID().toString().substring(0, 8);
    user.setUsername("u-" + unique);
    user.setEmail(unique + "@test.dev");
    user.setPassword("not-used");
    user.setName(name);
    user.setRole(Role.USER);
    user.setStatus(UserStatus.ACTIVE);
    return em.persist(user);
  }

  private ChannelEntity channel() {
    ChannelEntity channel = new ChannelEntity();
    channel.setName("reactions");
    channel.setBoundaryType(BoundaryType.PUBLIC);
    channel.setChannelType(ChannelType.LOUNGE);
    channel.setTotalOfMembers(2);
    channel.setCreator(alice);
    return em.persist(channel);
  }

  private Long message() {
    MessageEntity message = new MessageEntity();
    message.setUser(alice);
    message.setChannel(channel);
    message.setContentType(ContentType.TEXT);
    message.setContent("m" + ++sequence);
    message.setSeq((long) sequence);
    message.setCreatedTime(T0.plusSeconds(sequence));
    return em.persistAndFlush(message).getId();
  }
}
