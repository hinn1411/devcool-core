package com.devcool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.support.PostgresTestContainer;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V6 (P3-T08): seed at V5, then add message.reply_to_id. Expand only. */
class V6ReplyToIT {

  private static final long ALICE = 1, CHANNEL = 10, OLD_A = 100, OLD_B = 101;

  private String url;
  private JdbcTemplate jdbc;

  @BeforeEach
  void seedAtV5ThenApplyV6() {
    url = PostgresTestContainer.freshDatabase("v6_it_");
    var pg = PostgresTestContainer.POSTGRES;
    jdbc = new JdbcTemplate(new DriverManagerDataSource(url, pg.getUsername(), pg.getPassword()));

    migrateTo("5");
    jdbc.batchUpdate(
        "insert into app_user (id, role, status) values (1, 'USER', 'ACTIVE')",
        """
        insert into channel (id, name, boundary, total_of_members, channel_type, last_seq) values
          (10, 'lounge', 'PUBLIC', 1, 'LOUNGE', 2)
        """,
        """
        insert into message (id, user_id, channel_id, content_type, created_time, seq) values
          (100, 1, 10, 'TEXT', '2026-11-01 08:00:00+00', 1),
          (101, 1, 10, 'TEXT', '2026-11-01 08:30:00+00', 2)
        """);
    migrateTo("6");
  }

  @Test
  void existingMessages_areNotReplies() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from message where reply_to_id is not null", Long.class))
        .isZero();
  }

  @Test
  void column_isANullableBigint() {
    Map<String, Object> column =
        jdbc.queryForMap(
            """
            select data_type, is_nullable from information_schema.columns
            where table_name = 'message' and column_name = 'reply_to_id'
            """);

    assertThat(column).containsEntry("data_type", "bigint").containsEntry("is_nullable", "YES");
  }

  @Test
  void insertWithoutTheColumn_stillWorks_forTasksStillOnTheOldVersion() {
    // Rolling deploy: a V5 task's INSERT doesn't name reply_to_id.
    insertMessage(102, 3, null);

    assertThat(replyToOf(102)).isNull();
  }

  @Test
  void replyToAnExistingMessage_isStored() {
    insertMessage(102, 3, OLD_A);

    assertThat(replyToOf(102)).isEqualTo(OLD_A);
  }

  @Test
  void replyToAnUnknownMessage_isRejectedByTheForeignKey() {
    assertThatThrownBy(() -> insertMessage(102, 3, 999L))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_message_reply_to");
  }

  @Test
  void aReplyCanBeTheParentOfAnotherReply() {
    // Flat preview, but the column itself allows a chain (reply to a reply).
    insertMessage(102, 3, OLD_B);
    insertMessage(103, 4, 102L);

    assertThat(replyToOf(103)).isEqualTo(102L);
  }

  private void insertMessage(long id, long seq, Long replyToId) {
    if (replyToId == null) {
      jdbc.update(
          "insert into message (id, user_id, channel_id, content_type, created_time, seq)"
              + " values (?, ?, ?, 'TEXT', now(), ?)",
          id,
          ALICE,
          CHANNEL,
          seq);
      return;
    }
    jdbc.update(
        "insert into message (id, user_id, channel_id, content_type, created_time, seq,"
            + " reply_to_id) values (?, ?, ?, 'TEXT', now(), ?, ?)",
        id,
        ALICE,
        CHANNEL,
        seq,
        replyToId);
  }

  private Long replyToOf(long id) {
    return jdbc.queryForObject("select reply_to_id from message where id = ?", Long.class, id);
  }

  private void migrateTo(String version) {
    var pg = PostgresTestContainer.POSTGRES;
    Flyway.configure()
        .dataSource(url, pg.getUsername(), pg.getPassword())
        .target(version)
        .load()
        .migrate();
  }
}
