package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.chat.exception.MessageVersionConflictException;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.port.in.EditMessageUseCase;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.devcool.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The optimistic-lock guard through real transactions (P3-T06); every edit commits. */
class EditMessageConcurrencyIT extends AbstractIntegrationTest {

  private static final int THREADS = 20;

  @Autowired EditMessageUseCase editMessage;
  @Autowired JdbcTemplate jdbc;

  private long alice;
  private long message;

  @BeforeEach
  void seedAliceMessage() {
    alice = user();
    message = messageIn(channelWith(alice), alice);
  }

  @Test
  void staleClient_getsConflict_andTheNewerEditSurvives() {
    editMessage.edit(new EditMessageCommand(alice, message, "from the phone", 0));

    // The laptop still shows v0.
    assertThatThrownBy(
            () -> editMessage.edit(new EditMessageCommand(alice, message, "from the laptop", 0)))
        .isInstanceOf(MessageVersionConflictException.class);
    assertThat(stored("content")).isEqualTo("from the phone");
    assertThat(stored("version")).isEqualTo(1);
  }

  @Test
  void concurrentEditsOnOneVersion_exactlyOneWins() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<EditedMessage>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      for (int i = 0; i < THREADS; i++) {
        EditMessageCommand cmd = new EditMessageCommand(alice, message, "edit " + i, 0);
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return editMessage.edit(cmd);
                }));
      }
      start.countDown();

      int wins = 0, conflicts = 0;
      for (Future<EditedMessage> f : futures) {
        try {
          f.get(30, TimeUnit.SECONDS);
          wins++;
        } catch (ExecutionException ex) {
          assertThat(ex.getCause()).isInstanceOf(MessageVersionConflictException.class);
          conflicts++;
        }
      }
      assertThat(wins).isEqualTo(1);
      assertThat(conflicts).isEqualTo(THREADS - 1);
    }
    assertThat(stored("version")).isEqualTo(1);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private Object stored(String column) {
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

  private long channelWith(long member) {
    long id =
        jdbc.queryForObject(
            """
            insert into channel (id, name, boundary, total_of_members, channel_type)
            values (nextval('channel_seq'), 'edit-it', 'PUBLIC', 1, 'LOUNGE') returning id
            """,
            Long.class);
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, 'CREATOR', now())
        """,
        member,
        id);
    return id;
  }

  /** A TEXT message sent now (inside the 24 h window), at version 0. */
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
