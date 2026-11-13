package com.devcool.adapters.out.persistence.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.out.persistence.channel.entity.ChannelEntity;
import com.devcool.adapters.out.persistence.member.entity.MemberEntity;
import com.devcool.adapters.out.persistence.member.mapper.MemberMapperImpl;
import com.devcool.adapters.out.persistence.user.entity.UserEntity;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.devcool.support.AbstractJpaIT;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** MemberPort read marker on real Postgres (P3-T10 loop 1). */
@Import({MemberAdapter.class, MemberMapperImpl.class})
class MemberAdapterIT extends AbstractJpaIT {

  private static final long LAST_SEQ = 10;

  @Autowired MemberPort members;
  @Autowired TestEntityManager em;

  private UserEntity alice;
  private ChannelEntity channel;

  @BeforeEach
  void seed() {
    alice = user();
    channel = channel(alice);
    member(alice, channel);
    setLastSeq(channel, LAST_SEQ);
  }

  @Test
  void advance_movesForward_neverBack() {
    assertThat(members.advanceLastReadSeq(channel.getId(), alice.getId(), 5)).isTrue();
    assertThat(members.advanceLastReadSeq(channel.getId(), alice.getId(), 3)).isTrue();

    assertThat(lastReadSeq(alice, channel)).isEqualTo(5);
  }

  @Test
  void advance_clampsToLastSeq() {
    members.advanceLastReadSeq(channel.getId(), alice.getId(), 9_999);

    assertThat(lastReadSeq(alice, channel)).isEqualTo(LAST_SEQ);
  }

  @Test
  void advance_nonMember_matchesNoRow() {
    UserEntity outsider = user();

    assertThat(members.advanceLastReadSeq(channel.getId(), outsider.getId(), 5)).isFalse();
    assertThat(lastReadSeq(alice, channel)).isZero();
  }

  // ---- seed helpers ----------------------------------------------------------------------------

  private long lastReadSeq(UserEntity user, ChannelEntity in) {
    em.flush();
    em.clear();
    return ((Number)
            em.getEntityManager()
                .createNativeQuery(
                    "select last_read_seq from member where user_id = ?1 and channel_id = ?2")
                .setParameter(1, user.getId())
                .setParameter(2, in.getId())
                .getSingleResult())
        .longValue();
  }

  private void setLastSeq(ChannelEntity target, long lastSeq) {
    em.flush();
    em.getEntityManager()
        .createNativeQuery("update channel set last_seq = ?1 where id = ?2")
        .setParameter(1, lastSeq)
        .setParameter(2, target.getId())
        .executeUpdate();
  }

  private UserEntity user() {
    UserEntity user = new UserEntity();
    String unique = UUID.randomUUID().toString().substring(0, 8);
    user.setUsername("u-" + unique);
    user.setEmail(unique + "@test.dev");
    user.setPassword("not-used");
    user.setName("User " + unique);
    user.setRole(Role.USER);
    user.setStatus(UserStatus.ACTIVE);
    return em.persist(user);
  }

  private ChannelEntity channel(UserEntity creator) {
    ChannelEntity channel = new ChannelEntity();
    channel.setName("read-state");
    channel.setBoundaryType(BoundaryType.PUBLIC);
    channel.setChannelType(ChannelType.LOUNGE);
    channel.setTotalOfMembers(1);
    channel.setCreator(creator);
    return em.persist(channel);
  }

  private void member(UserEntity user, ChannelEntity in) {
    em.persist(
        MemberEntity.builder()
            .user(user)
            .channel(in)
            .role(MemberType.CREATOR)
            .joinedTime(Instant.now())
            .build());
  }
}
