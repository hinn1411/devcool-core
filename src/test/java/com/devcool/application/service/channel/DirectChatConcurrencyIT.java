package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.channel.model.DirectChat;
import com.devcool.domain.channel.port.in.OpenDirectChatUseCase;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;
import com.devcool.support.AbstractIntegrationTest;
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

/** Both users open their DM at once. Not @Transactional: every call commits. */
class DirectChatConcurrencyIT extends AbstractIntegrationTest {

  private static final int THREADS = 20;

  @Autowired OpenDirectChatUseCase directChats;
  @Autowired JdbcTemplate jdbc;

  @Test
  void parallelOpensFromBothSides_createExactlyOneDirectChat() throws Exception {
    long alice = user();
    long bob = user();
    CountDownLatch start = new CountDownLatch(1);
    List<DirectChat> results = new ArrayList<>();

    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      List<Future<DirectChat>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        // Half A→B, half B→A: the pair key must be order-free
        OpenDirectChatCommand command =
            i % 2 == 0
                ? new OpenDirectChatCommand(alice, bob)
                : new OpenDirectChatCommand(bob, alice);
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return directChats.openDirectChat(command);
                }));
      }
      start.countDown();
      for (Future<DirectChat> f : futures) {
        results.add(f.get(30, TimeUnit.SECONDS));
      }
    }

    assertThat(results).extracting(DirectChat::channelId).containsOnly(results.get(0).channelId());
    assertThat(results).filteredOn(DirectChat::created).hasSize(1);
    assertThat(privateChatsOf(alice, bob)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from direct_chat where user_low_id = ? and user_high_id = ?",
                Long.class,
                Math.min(alice, bob),
                Math.max(alice, bob)))
        .isEqualTo(1);
  }

  /** PRIVATE_CHAT channels both users are in, so a stray uncommitted-then-kept DM is counted. */
  private long privateChatsOf(long a, long b) {
    return jdbc.queryForObject(
        """
        select count(*) from channel c
        where c.channel_type = 'PRIVATE_CHAT'
          and exists (select 1 from member m where m.channel_id = c.id and m.user_id = ?)
          and exists (select 1 from member m where m.channel_id = c.id and m.user_id = ?)
        """,
        Long.class,
        a,
        b);
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
}
