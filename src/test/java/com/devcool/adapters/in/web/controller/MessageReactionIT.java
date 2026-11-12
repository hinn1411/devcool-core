package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.chat.port.in.ReactToMessageUseCase;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import com.devcool.domain.user.port.out.UserPort;
import com.devcool.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Reactions through the real filter chain, real tokens and committed transactions (P3-T09). */
class MessageReactionIT extends AbstractIntegrationTest {

  private static final String THUMBS = "👍";
  private static final int THREADS = 20;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;
  @Autowired ReactToMessageUseCase reactions;

  private long alice;
  private long outsider;
  private long channel;
  private long message;

  @BeforeEach
  void seed() {
    alice = user();
    outsider = user();
    channel = channel(alice);
    message = messageIn(channel, alice, 1);
  }

  @Test
  void memberReacts_204_thenHistoryShowsTheCountAndReactedByMe() throws Exception {
    react(alice, message, THUMBS).andExpect(status().isNoContent());
    react(alice, message, THUMBS).andExpect(status().isNoContent());

    mvc.perform(
            get("/api/v1/channels/{id}/messages", channel).header(auth(alice)[0], auth(alice)[1]))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].reactions[0].emoji").value(THUMBS))
        .andExpect(jsonPath("$.data.items[0].reactions[0].count").value(1))
        .andExpect(jsonPath("$.data.items[0].reactions[0].reactedByMe").value(true));
  }

  @Test
  void unreact_absentReaction_is204() throws Exception {
    unreact(alice, message, THUMBS).andExpect(status().isNoContent());
  }

  @Test
  void withoutToken_is401() throws Exception {
    mvc.perform(put("/api/v1/messages/{id}/reactions/{emoji}", message, THUMBS))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void nonMember_is404_onBothVerbs_andWritesNothing() throws Exception {
    react(outsider, message, THUMBS).andExpect(status().isNotFound());
    unreact(outsider, message, THUMBS).andExpect(status().isNotFound());

    assertThat(rows(message)).isZero();
  }

  @Test
  void deletedMessage_is404() throws Exception {
    jdbc.update("update message set deleted_time = now() where id = ?", message);

    react(alice, message, THUMBS).andExpect(status().isNotFound());
    assertThat(rows(message)).isZero();
  }

  @Test
  void invalidEmoji_is400() throws Exception {
    react(alice, message, "abc").andExpect(status().isBadRequest());
    assertThat(rows(message)).isZero();
  }

  @Test
  void concurrentReacts_sameUserAndEmoji_leaveOneRow() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      for (int i = 0; i < THREADS; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  reactions.react(new ReactionCommand(alice, message, THUMBS));
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS); // throws if any react failed
      }
    }
    assertThat(rows(message)).isEqualTo(1);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private ResultActions react(long user, long messageId, String emoji) throws Exception {
    String[] header = auth(user);
    return mvc.perform(
        put("/api/v1/messages/{id}/reactions/{emoji}", messageId, emoji)
            .header(header[0], header[1]));
  }

  private ResultActions unreact(long user, long messageId, String emoji) throws Exception {
    String[] header = auth(user);
    return mvc.perform(
        delete("/api/v1/messages/{id}/reactions/{emoji}", messageId, emoji)
            .header(header[0], header[1]));
  }

  private String[] auth(long user) {
    String token = tokenIssuer.issue(userPort.findById(user).orElseThrow()).accessToken();
    return new String[] {HttpHeaders.AUTHORIZATION, "Bearer " + token};
  }

  /** Counted with SQL only after the call under test, so the table is the stub's problem. */
  private int rows(long messageId) {
    return jdbc.queryForObject(
        "select count(*) from message_reaction where message_id = ?", Integer.class, messageId);
  }

  private long user() {
    String name = "u" + UUID.randomUUID().toString().substring(0, 8);
    return jdbc.queryForObject(
        """
        insert into app_user (id, user_name, email, role, status, token_version)
        values (nextval('app_user_seq'), ?, ?, 'USER', 'ACTIVE', 0) returning id
        """,
        Long.class,
        name,
        name + "@test.dev");
  }

  private long channel(long creator) {
    long id =
        jdbc.queryForObject(
            """
            insert into channel (id, name, boundary, total_of_members, channel_type)
            values (nextval('channel_seq'), 'reaction-it', 'PRIVATE', 1, 'LOUNGE') returning id
            """,
            Long.class);
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, 'CREATOR', now())
        """,
        creator,
        id);
    jdbc.update("update channel set last_seq = 1 where id = ?", id);
    return id;
  }

  private long messageIn(long channelId, long sender, long seq) {
    return jdbc.queryForObject(
        """
        insert into message (id, user_id, channel_id, content, content_type, created_time, seq)
        values (nextval('message_seq'), ?, ?, 'hello', 'TEXT', now(), ?) returning id
        """,
        Long.class,
        sender,
        channelId,
        seq);
  }
}
