package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.channel.port.in.LeaveChannelUseCase;
import com.devcool.domain.channel.port.in.TransferLeaderUseCase;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;
import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;
import com.devcool.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Member changes lock the channel row first. Not @Transactional: every call commits. */
class MemberManagementConcurrencyIT extends AbstractIntegrationTest {

  private static final int THREADS = 20;
  private static final int LOUNGE_MAX_PEOPLE = 11;

  @Autowired UpdateChannelUseCase channelUpdater;
  @Autowired TransferLeaderUseCase leaderTransferrer;
  @Autowired LeaveChannelUseCase channelLeaver;
  @Autowired JdbcTemplate jdbc;

  @Test
  void parallelAddsToANearlyFullLounge_neverExceedTheCap() throws Exception {
    long creator = user();
    long lounge = channel("LOUNGE", null);
    member(lounge, creator, "CREATOR");
    for (int i = 0; i < LOUNGE_MAX_PEOPLE - 2; i++) {
      member(lounge, user(), "MEMBER");
    }
    setTotal(lounge);
    List<Callable<Boolean>> adds = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      long newcomer = user();
      adds.add(
          () ->
              channelUpdater.addMember(lounge, new AddMembersCommand(creator, List.of(newcomer))));
    }

    List<Boolean> succeeded = inParallel(adds);

    assertThat(succeeded).filteredOn(Boolean::booleanValue).hasSize(1);
    assertThat(total(lounge)).isEqualTo(LOUNGE_MAX_PEOPLE);
    assertThat(rows(lounge)).isEqualTo(LOUNGE_MAX_PEOPLE);
  }

  @Test
  void transferToAMemberRacingThatMemberLeaving_alwaysLeavesAMemberLeader() throws Exception {
    // One race per forum: leader A hands over to B while B leaves
    List<long[]> forums = new ArrayList<>();
    List<Callable<Boolean>> ops = new ArrayList<>();
    for (int i = 0; i < THREADS / 2; i++) {
      long creator = user();
      long a = user();
      long b = user();
      long forum = channel("FORUM", a);
      member(forum, creator, "CREATOR");
      member(forum, a, "LEADER");
      member(forum, b, "MEMBER");
      setTotal(forum);
      forums.add(new long[] {forum, a, b});
      ops.add(
          () -> {
            leaderTransferrer.transferLeader(new TransferLeaderCommand(forum, a, b));
            return true;
          });
      ops.add(
          () -> {
            channelLeaver.leave(new LeaveChannelCommand(forum, b));
            return true;
          });
    }

    inParallel(ops);

    for (long[] f : forums) {
      long forum = f[0];
      Long leaderId =
          jdbc.queryForObject("select leader_id from channel where id = ?", Long.class, forum);
      assertThat(roleOf(forum, leaderId))
          .as("forum %d leader %d", forum, leaderId)
          .isIn("LEADER", "CREATOR");
      assertThat(total(forum)).isEqualTo(rows(forum));
    }
  }

  /** A rejected op counts as false. */
  private List<Boolean> inParallel(List<Callable<Boolean>> ops) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(ops.size())) {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (Callable<Boolean> op : ops) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return op.call();
                  } catch (RuntimeException rejected) {
                    return false;
                  }
                }));
      }
      start.countDown();
      List<Boolean> results = new ArrayList<>();
      for (Future<Boolean> f : futures) {
        results.add(f.get(30, TimeUnit.SECONDS));
      }
      return results;
    }
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

  private long channel(String type, Long leaderId) {
    return jdbc.queryForObject(
        """
        insert into channel (id, name, boundary, total_of_members, channel_type, leader_id)
        values (nextval('channel_seq'), 'member-it', 'PUBLIC', 0, ?, ?) returning id
        """,
        Long.class,
        type,
        leaderId);
  }

  private void member(long channelId, long userId, String role) {
    jdbc.update(
        """
        insert into member (id, user_id, channel_id, role, joined_time)
        values (nextval('member_seq'), ?, ?, ?, now())
        """,
        userId,
        channelId,
        role);
  }

  private void setTotal(long channelId) {
    jdbc.update(
        "update channel set total_of_members = (select count(*) from member where channel_id = ?)"
            + " where id = ?",
        channelId,
        channelId);
  }

  private int total(long channelId) {
    return jdbc.queryForObject(
        "select total_of_members from channel where id = ?", Integer.class, channelId);
  }

  private int rows(long channelId) {
    return jdbc.queryForObject(
        "select count(*) from member where channel_id = ?", Integer.class, channelId);
  }

  private String roleOf(long channelId, Long userId) {
    return jdbc
        .queryForList(
            "select role from member where channel_id = ? and user_id = ?",
            String.class,
            channelId,
            userId)
        .stream()
        .findFirst()
        .orElse("NOT A MEMBER");
  }
}
