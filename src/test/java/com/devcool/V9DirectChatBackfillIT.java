package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.devcool.support.AbstractMigrationIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V9 on a database that already holds direct chats (P3-T12): seed at V8 the way the pre-V9 app
 * wrote them (duplicates included), then apply the rest and check the backfill and the pair key.
 */
class V9DirectChatBackfillIT extends AbstractMigrationIT {

  @BeforeEach
  void seedAtV8ThenMigrate() {
    openFreshDatabase("v9_it_");

    migrateTo("8");
    jdbc.batchUpdate(
        """
        insert into app_user (id, role, status) values
          (1, 'USER', 'ACTIVE'), (2, 'USER', 'ACTIVE'), (3, 'USER', 'ACTIVE')
        """,
        """
        insert into channel (id, name, boundary, total_of_members, channel_type) values
          (10, 'dm-1-2', 'PRIVATE', 2, 'PRIVATE_CHAT'),
          (11, 'dm-2-1-dup', 'PRIVATE', 2, 'PRIVATE_CHAT'),
          (12, 'dm-1-3', 'PRIVATE', 2, 'PRIVATE_CHAT'),
          (13, 'lounge-1-2', 'PUBLIC', 2, 'LOUNGE'),
          (14, 'dm-lonely', 'PRIVATE', 1, 'PRIVATE_CHAT')
        """,
        """
        insert into member (id, user_id, channel_id, role, joined_time) values
          (1, 1, 10, 'CREATOR', now()), (2, 2, 10, 'MEMBER', now()),
          (3, 2, 11, 'CREATOR', now()), (4, 1, 11, 'MEMBER', now()),
          (5, 3, 12, 'CREATOR', now()), (6, 1, 12, 'MEMBER', now()),
          (7, 1, 13, 'CREATOR', now()), (8, 2, 13, 'MEMBER', now()),
          (9, 1, 14, 'CREATOR', now())
        """);
    migrateTo(null);
  }

  @Test
  void indexesTheOldestPrivateChatPerPair_lowIdFirst() {
    assertThat(
            jdbc.queryForList(
                "select user_low_id, user_high_id, channel_id from direct_chat order by channel_id"))
        .extracting(r -> r.get("user_low_id"), r -> r.get("user_high_id"), r -> r.get("channel_id"))
        .containsExactly(tuple(1L, 2L, 10L), tuple(1L, 3L, 12L));
  }

  @Test
  void leavesDuplicatesAndOtherChannelsInPlace() {
    assertThat(jdbc.queryForObject("select count(*) from channel", Long.class)).isEqualTo(5);
  }

  @Test
  void rejectsASecondChannelForAPair() {
    assertThatThrownBy(() -> insertPair(1, 2, 11))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsAPairStoredHighIdFirstOrWithYourself() {
    assertThatThrownBy(() -> insertPair(3, 2, 11))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertPair(2, 2, 11))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertPair(long low, long high, long channelId) {
    jdbc.update(
        "insert into direct_chat (user_low_id, user_high_id, channel_id) values (?, ?, ?)",
        low,
        high,
        channelId);
  }
}
