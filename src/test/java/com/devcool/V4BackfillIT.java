package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.devcool.support.PostgresTestContainer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V4 on a database that already holds messages (P3-T02): migrate to V3, seed rows the way the
 * pre-V4 app wrote them, then apply V4 and check the backfill and the new unique keys.
 */
class V4BackfillIT {

  private static final long ALICE = 1, BOB = 2;
  private static final long BUSY = 10, QUIET = 20, EMPTY = 30;

  private String url;
  private JdbcTemplate jdbc;

  @BeforeEach
  void seedAtV3ThenApplyV4() {
    url = PostgresTestContainer.freshDatabase("v4_it_");
    var pg = PostgresTestContainer.POSTGRES;
    jdbc = new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()));

    migrateTo("3");
    jdbc.batchUpdate(
        "insert into app_user (id, role, status) values (1, 'USER', 'ACTIVE'), (2, 'USER', 'ACTIVE')",
        """
        insert into channel (id, name, boundary, total_of_members, channel_type) values
          (10, 'busy', 'PUBLIC', 2, 'LOUNGE'),
          (20, 'quiet', 'PUBLIC', 1, 'LOUNGE'),
          (30, 'empty', 'PUBLIC', 1, 'LOUNGE')
        """,
        // Ids interleave across channels (one global sequence), so seq != id
        """
        insert into message (id, user_id, channel_id, content_type, created_time) values
          (100, 1, 10, 'TEXT', '2026-10-01 10:00:00+00'),
          (101, 2, 20, 'TEXT', '2026-10-01 10:01:00+00'),
          (102, 1, 10, 'TEXT', '2026-10-01 10:02:00+00'),
          (103, 2, 10, 'TEXT', '2026-10-01 10:03:00+00'),
          (105, 2, 20, 'TEXT', '2026-10-01 10:05:00+00')
        """,
        """
        insert into member (id, user_id, channel_id, role, joined_time) values
          (1, 1, 10, 'CREATOR', now()),
          (2, 2, 10, 'MEMBER', now()),
          (3, 2, 20, 'CREATOR', now()),
          (4, 1, 30, 'CREATOR', now())
        """);
    migrateTo("4");
  }

  @Test
  void numbersExistingMessagesOneToNPerChannelInIdOrder() {
    assertThat(seqById(BUSY))
        .containsExactly(Map.entry(100L, 1L), Map.entry(102L, 2L), Map.entry(103L, 3L));
    assertThat(seqById(QUIET)).containsExactly(Map.entry(101L, 1L), Map.entry(105L, 2L));
  }

  @Test
  void setsEachChannelsLastSeqAndLastMessageAt() {
    assertThat(lastSeq(BUSY)).isEqualTo(3);
    assertThat(lastSeq(QUIET)).isEqualTo(2);
    assertThat(lastSeq(EMPTY)).isZero();

    assertThat(lastMessageAt(BUSY)).isEqualTo(OffsetDateTime.parse("2026-10-01T10:03:00Z"));
    assertThat(lastMessageAt(QUIET)).isEqualTo(OffsetDateTime.parse("2026-10-01T10:05:00Z"));
    assertThat(lastMessageAt(EMPTY)).isNull();
  }

  @Test
  void marksExistingHistoryAsReadForEveryMember() {
    List<Map<String, Object>> members =
        jdbc.queryForList("select channel_id, last_read_seq from member order by id");

    assertThat(members)
        .extracting(m -> m.get("channel_id"), m -> m.get("last_read_seq"))
        .containsExactly(tuple(BUSY, 3L), tuple(BUSY, 3L), tuple(QUIET, 2L), tuple(EMPTY, 0L));
  }

  @Test
  void startsEveryExistingMessageAtVersionZeroWithNoClientMsgId() {
    assertThat(jdbc.queryForList("select distinct version from message", Integer.class))
        .containsExactly(0);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from message where client_msg_id is null", Long.class))
        .isEqualTo(5);
  }

  @Test
  void rejectsASecondMessageWithTheSameSeqInOneChannel() {
    assertThatThrownBy(() -> insertMessage(106, ALICE, BUSY, 1L, null))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("uk_message_channel_seq");

    // The same seq in another channel is fine
    insertMessage(107, ALICE, EMPTY, 1L, null);
  }

  @Test
  void rejectsTheSameClientMsgIdTwiceFromOneSender() {
    UUID clientMsgId = UUID.randomUUID();
    insertMessage(106, ALICE, BUSY, 4L, clientMsgId);

    assertThatThrownBy(() -> insertMessage(107, ALICE, BUSY, 5L, clientMsgId))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("uk_message_user_client_msg");

    // Keys are per sender: another user may reuse the same UUID
    insertMessage(108, BOB, BUSY, 6L, clientMsgId);
  }

  @Test
  void allowsMessagesWithoutSeqOrClientMsgId_asOldTasksStillInsertThem() {
    // What a pre-V4 task writes during the rollout: neither column, defaults for the rest
    jdbc.update(
        "insert into message (id, user_id, channel_id, content_type, created_time) values (?, ?, ?, 'TEXT', now())",
        106,
        ALICE,
        BUSY);
    jdbc.update(
        "insert into message (id, user_id, channel_id, content_type, created_time) values (?, ?, ?, 'TEXT', now())",
        107,
        ALICE,
        BUSY);

    assertThat(jdbc.queryForObject("select count(*) from message where seq is null", Long.class))
        .isEqualTo(2);
  }

  private void migrateTo(String version) {
    var pg = PostgresTestContainer.POSTGRES;
    Flyway.configure()
        .dataSource(url, pg.getUsername(), pg.getPassword())
        .target(version)
        .load()
        .migrate();
  }

  private void insertMessage(long id, long userId, long channelId, Long seq, UUID clientMsgId) {
    jdbc.update(
        """
        insert into message (id, user_id, channel_id, content_type, created_time, seq, client_msg_id)
        values (?, ?, ?, 'TEXT', now(), ?, ?)
        """,
        id,
        userId,
        channelId,
        seq,
        clientMsgId);
  }

  private List<Map.Entry<Long, Long>> seqById(long channelId) {
    return jdbc.query(
        "select id, seq from message where channel_id = ? order by id",
        (rs, i) -> Map.entry(rs.getLong("id"), (Long) rs.getObject("seq")),
        channelId);
  }

  private long lastSeq(long channelId) {
    return jdbc.queryForObject("select last_seq from channel where id = ?", Long.class, channelId);
  }

  private OffsetDateTime lastMessageAt(long channelId) {
    return jdbc.queryForObject(
        "select last_message_at from channel where id = ?",
        (rs, i) -> rs.getObject(1, OffsetDateTime.class),
        channelId);
  }
}
