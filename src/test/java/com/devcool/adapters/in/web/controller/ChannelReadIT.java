package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** PUT /channels/{id}/read over HTTP with real tokens and commits (P3-T10 loop 1). */
class ChannelReadIT extends AbstractIntegrationTest {

  private static final int THREADS = 20;
  private static final long LAST_SEQ = 20;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TokenIssuerPort tokenIssuer;
  @Autowired UserPort userPort;
  @Autowired MarkChannelReadUseCase reader;

  private long alice;
  private long outsider;
  private long channel;

  @BeforeEach
  void seed() {
    alice = user();
    outsider = user();
    channel = channel(alice);
  }

  @Test
  void member_204_andMarkerOnlyMovesForward() throws Exception {
    markRead(alice, channel, "{\"seq\": 5}").andExpect(status().isNoContent());
    markRead(alice, channel, "{\"seq\": 3}").andExpect(status().isNoContent());
    markRead(alice, channel, "{\"seq\": 5}").andExpect(status().isNoContent());

    assertThat(lastReadSeq(alice)).isEqualTo(5);
  }

  @Test
  void seqAboveLastSeq_isClamped() throws Exception {
    markRead(alice, channel, "{\"seq\": 9999}").andExpect(status().isNoContent());

    assertThat(lastReadSeq(alice)).isEqualTo(LAST_SEQ);
  }

  @Test
  void missingSeq_is400() throws Exception {
    markRead(alice, channel, "{}").andExpect(status().isBadRequest());
    assertThat(lastReadSeq(alice)).isZero();
  }

  @Test
  void negativeSeq_is400() throws Exception {
    markRead(alice, channel, "{\"seq\": -1}").andExpect(status().isBadRequest());
    assertThat(lastReadSeq(alice)).isZero();
  }

  @Test
  void nonMember_is404_andWritesNothing() throws Exception {
    markRead(outsider, channel, "{\"seq\": 5}").andExpect(status().isNotFound());

    assertThat(lastReadSeq(alice)).isZero();
  }

  @Test
  void unknownChannel_is404() throws Exception {
    markRead(alice, Long.MAX_VALUE, "{\"seq\": 5}").andExpect(status().isNotFound());
  }

  @Test
  void withoutToken_is401() throws Exception {
    mvc.perform(
            put("/api/v1/channels/{id}/read", channel)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seq\": 5}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void concurrentReads_endAtTheHighestSeq() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      for (int i = 1; i <= THREADS; i++) {
        long seq = i;
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  reader.markRead(new MarkChannelReadCommand(channel, alice, seq));
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS); // throws if any call failed
      }
    }
    assertThat(lastReadSeq(alice)).isEqualTo(THREADS);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private ResultActions markRead(long user, long channelId, String body) throws Exception {
    return mvc.perform(
        put("/api/v1/channels/{id}/read", channelId)
            .header(AUTHORIZATION, bearer(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private String bearer(long user) {
    return "Bearer " + tokenIssuer.issue(userPort.findById(user).orElseThrow()).accessToken();
  }

  private long lastReadSeq(long user) {
    return jdbc.queryForObject(
        "select last_read_seq from member where user_id = ? and channel_id = ?",
        Long.class,
        user,
        channel);
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
            insert into channel (id, name, boundary, total_of_members, channel_type, last_seq)
            values (nextval('channel_seq'), 'read-it', 'PRIVATE', 1, 'LOUNGE', ?) returning id
            """,
            Long.class,
            LAST_SEQ);
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, 'CREATOR', now())
        """,
        creator,
        id);
    return id;
  }
}
