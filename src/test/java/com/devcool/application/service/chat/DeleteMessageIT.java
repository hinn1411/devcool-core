package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.chat.port.in.DeleteMessageUseCase;
import com.devcool.domain.chat.port.in.command.DeleteMessageCommand;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Delete through real transactions and real member roles (P3-T07); every delete commits. */
class DeleteMessageIT extends AbstractIntegrationTest {

  private static final int THREADS = 20;

  @Autowired DeleteMessageUseCase deleteMessage;
  @Autowired JdbcTemplate jdbc;

  @Test
  void repeatDelete_succeeds_andKeepsTheFirstTimestamp() {
    long alice = user();
    long message = messageIn(channel("LOUNGE", alice, "CREATOR"), alice);

    deleteMessage.delete(new DeleteMessageCommand(alice, message));
    Timestamp first = deletedTime(message);
    deleteMessage.delete(new DeleteMessageCommand(alice, message));

    assertThat(first).isNotNull();
    assertThat(deletedTime(message)).isEqualTo(first);
    assertThat(stored(message, "content")).isEqualTo("original"); // soft: the row keeps it
  }

  @Test
  void concurrentDeletes_allSucceed_oneTimestamp() throws Exception {
    long alice = user();
    long message = messageIn(channel("LOUNGE", alice, "CREATOR"), alice);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  deleteMessage.delete(new DeleteMessageCommand(alice, message));
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS); // throws if any delete failed
      }
    }
    assertThat(deletedTime(message)).isNotNull();
  }

  @Test
  void forumLeader_deletesAMembersMessage() {
    long alice = user();
    long leader = user();
    long forum = channel("FORUM", alice, "MEMBER");
    join(forum, leader, "LEADER");
    long message = messageIn(forum, alice);

    deleteMessage.delete(new DeleteMessageCommand(leader, message));

    assertThat(deletedTime(message)).isNotNull();
  }

  @Test
  void loungeMember_cannotDeleteAnothersMessage() {
    long alice = user();
    long bob = user();
    long lounge = channel("LOUNGE", alice, "CREATOR");
    join(lounge, bob, "MEMBER");
    long message = messageIn(lounge, alice);

    assertThatThrownBy(() -> deleteMessage.delete(new DeleteMessageCommand(bob, message)))
        .isInstanceOf(ForbiddenException.class);
    assertThat(deletedTime(message)).isNull();
  }

  @Test
  void privateChatCreator_cannotDeleteTheOtherPersonsMessage() {
    long alice = user();
    long bob = user();
    long dm = channel("PRIVATE_CHAT", bob, "CREATOR");
    join(dm, alice, "MEMBER");
    long message = messageIn(dm, alice);

    assertThatThrownBy(() -> deleteMessage.delete(new DeleteMessageCommand(bob, message)))
        .isInstanceOf(ForbiddenException.class);
    assertThat(deletedTime(message)).isNull();
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private Timestamp deletedTime(long message) {
    return jdbc.queryForObject(
        "select deleted_time from message where id = ?", Timestamp.class, message);
  }

  private Object stored(long message, String column) {
    return jdbc.queryForObject(
        "select " + column + " from message where id = ?", Object.class, message);
  }

  private long user() {
    String name = "u" + UUID.randomUUID().toString().substring(0, 8);
    return jdbc.queryForObject(
        """
        insert into app_user (id, user_name, email, role, status)
        values (nextval('app_user_seq'), ?, ?, 'USER', 'ACTIVE') returning id
        """,
        Long.class,
        name,
        name + "@test.dev");
  }

  private long channel(String type, long firstMember, String role) {
    long id =
        jdbc.queryForObject(
            """
            insert into channel (id, name, boundary, total_of_members, channel_type)
            values (nextval('channel_seq'), 'delete-it', 'PRIVATE', 2, ?) returning id
            """,
            Long.class,
            type);
    join(id, firstMember, role);
    return id;
  }

  private void join(long channel, long user, String role) {
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, ?, now())
        """,
        user,
        channel,
        role);
  }

  private long messageIn(long channel, long sender) {
    return jdbc.queryForObject(
        """
        insert into message (id, user_id, channel_id, content, content_type, created_time, seq)
        values (nextval('message_seq'), ?, ?, 'original', 'TEXT', now(), 1) returning id
        """,
        Long.class,
        sender,
        channel);
  }
}
