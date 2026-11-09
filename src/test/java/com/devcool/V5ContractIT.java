package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.support.PostgresTestContainer;
import java.time.OffsetDateTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V5 contracts V4 (P3-T03a): migrate to V4, seed rows the way new (seq) and old (no seq) tasks
 * wrote them during the rollout, then apply V5 and check the NULL rows are appended, not
 * renumbered.
 */
class V5ContractIT {

  private static final long ALICE = 1;
  private static final long BUSY = 10, QUIET = 20, OLD_ONLY = 30;
  private static final OffsetDateTime QUIET_LAST_AT = OffsetDateTime.parse("2026-11-01T09:00:00Z");

  private String url;
  private JdbcTemplate jdbc;

  @BeforeEach
  void seedAtV4ThenApplyV5() {
    url = PostgresTestContainer.freshDatabase("v5_it_");
    var pg = PostgresTestContainer.POSTGRES;
    jdbc = new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()));

    migrateTo("4");
    jdbc.batchUpdate(
        "insert into app_user (id, role, status) values (1, 'USER', 'ACTIVE')",
        """
        insert into channel (id, name, boundary, total_of_members, channel_type, last_seq, last_message_at) values
          (10, 'busy', 'PUBLIC', 1, 'LOUNGE', 3, '2026-11-01 10:00:00+00'),
          (20, 'quiet', 'PUBLIC', 1, 'LOUNGE', 2, '2026-11-01 09:00:00+00'),
          (30, 'old-only', 'PUBLIC', 1, 'LOUNGE', 0, null)
        """,
        // New tasks wrote seq; old (pre-T03) tasks left it NULL. Ids interleave across channels.
        """
        insert into message (id, user_id, channel_id, content_type, created_time, seq) values
          (100, 1, 10, 'TEXT', '2026-11-01 08:00:00+00', 1),
          (101, 1, 20, 'TEXT', '2026-11-01 08:30:00+00', 1),
          (102, 1, 10, 'TEXT', '2026-11-01 09:00:00+00', 2),
          (103, 1, 10, 'TEXT', '2026-11-01 09:30:00+00', 3),
          (104, 1, 20, 'TEXT', '2026-11-01 09:00:00+00', 2),
          (105, 1, 30, 'TEXT', '2026-11-01 09:40:00+00', null),
          (107, 1, 10, 'TEXT', '2026-11-01 10:00:00+00', null),
          (106, 1, 10, 'TEXT', '2026-11-01 09:50:00+00', null)
        """);
    migrateTo("5");
  }

  @Test
  void appendsNullSeqRowsAfterLastSeqInIdOrder() {
    assertThat(seqById(BUSY))
        .containsExactly(
            Map.entry(100L, 1L),
            Map.entry(102L, 2L),
            Map.entry(103L, 3L),
            Map.entry(106L, 4L),
            Map.entry(107L, 5L));
    assertThat(lastSeq(BUSY)).isEqualTo(5);

    // Numbered per channel: a channel with only seq-less rows starts at 1
    assertThat(seqById(OLD_ONLY)).containsExactly(Map.entry(105L, 1L));
    assertThat(lastSeq(OLD_ONLY)).isEqualTo(1);
  }

  @Test
  void leavesChannelsWithoutNullRowsUntouched() {
    assertThat(seqById(QUIET)).containsExactly(Map.entry(101L, 1L), Map.entry(104L, 2L));
    assertThat(lastSeq(QUIET)).isEqualTo(2);
    assertThat(lastMessageAt(QUIET)).isEqualTo(QUIET_LAST_AT);
  }

  @Test
  void rejectsAMessageWithoutSeq() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into message (id, user_id, channel_id, content_type, created_time) values (?, ?, ?, 'TEXT', now())",
                    108,
                    ALICE,
                    QUIET))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("seq");
  }

  private void migrateTo(String version) {
    var pg = PostgresTestContainer.POSTGRES;
    Flyway.configure()
        .dataSource(url, pg.getUsername(), pg.getPassword())
        .target(version)
        .load()
        .migrate();
  }

  private List<Map.Entry<Long, Long>> seqById(long channelId) {
    return jdbc.query(
        "select id, seq from message where channel_id = ? order by id",
        (rs, i) -> new SimpleEntry<>(rs.getLong("id"), (Long) rs.getObject("seq")),
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
