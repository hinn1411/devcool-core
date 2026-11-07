package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.support.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seq assignment and idempotent send through real transactions and row locks (P3-T03, ADR-0012).
 * Not @Transactional: every send commits, as in production. Each test seeds its own channel.
 */
class SendMessageSeqIT extends AbstractIntegrationTest {

  private static final int THREADS = 20;

  @Autowired SaveMessageUseCase saveMessage;
  @Autowired JdbcTemplate jdbc;

  private long alice;
  private long outsider;
  private long channel;

  @BeforeEach
  void seedChannelWithAliceAsMember() {
    alice = user();
    outsider = user();
    channel = channelWith(alice);
  }

  @Test
  void firstSendInEmptyChannel_getsSeqOne_andBumpsTheChannel() {
    SavedMessage saved = saveMessage.save(send(alice, UUID.randomUUID()));

    assertThat(saved.seq()).isEqualTo(1L);
    assertThat(saved.duplicate()).isFalse();
    assertThat(storedSeq(saved.id())).isEqualTo(1L);
    assertThat(lastSeq()).isEqualTo(1L);
    assertThat(lastMessageAt()).isNotNull();
  }

  @Test
  void nextSend_getsTheChannelCounterPlusOne() {
    // No messages, but the counter says 41: the seq must come from channel.last_seq.
    jdbc.update("update channel set last_seq = 41 where id = ?", channel);

    SavedMessage saved = saveMessage.save(send(alice, UUID.randomUUID()));

    assertThat(saved.seq()).isEqualTo(42L);
    assertThat(lastSeq()).isEqualTo(42L);
  }

  @Test
  void returnsSameSeqOnRetriedClientMsgId() {
    UUID key = UUID.randomUUID();
    SavedMessage first = saveMessage.save(send(alice, key));

    SavedMessage retry = saveMessage.save(send(alice, key));

    // Same id, seq and createdTime as the first ACK would carry; only the flag differs.
    assertThat(retry)
        .isEqualTo(new SavedMessage(first.id(), first.seq(), first.createdTime(), true));
    assertThat(rowsWithKey(key)).isEqualTo(1);
    assertThat(lastSeq()).isEqualTo(1L);
  }

  @Test
  void parallelSends_getGaplessSeqs() throws Exception {
    List<SavedMessage> saved = inParallel(i -> send(alice, UUID.randomUUID()));

    assertThat(saved)
        .extracting(SavedMessage::seq)
        .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, THREADS).boxed().toList());
    assertThat(lastSeq()).isEqualTo(THREADS);
  }

  @Test
  void concurrentRetriesOfOneKey_storeOneRow_andBumpOnce() throws Exception {
    UUID key = UUID.randomUUID();

    List<SavedMessage> saved = inParallel(i -> send(alice, key));

    assertThat(saved).extracting(SavedMessage::id).containsOnly(saved.getFirst().id());
    assertThat(saved).extracting(SavedMessage::seq).containsOnly(1L);
    assertThat(saved).filteredOn(s -> !s.duplicate()).hasSize(1);
    assertThat(rowsWithKey(key)).isEqualTo(1);
    assertThat(lastSeq()).isEqualTo(1L);
  }

  @Test
  void keyReusedInAnotherChannel_isRejected_andNothingIsStoredThere() {
    UUID key = UUID.randomUUID();
    saveMessage.save(send(alice, key));
    long otherChannel = channelWith(alice);

    assertThatThrownBy(
            () ->
                saveMessage.save(
                    new CreateMessageCommand("hi", ContentType.TEXT, otherChannel, alice, key)))
        .isInstanceOf(ClientMsgIdReusedException.class);
    assertThat(rowsWithKey(key)).isEqualTo(1);
    assertThat(lastSeqOf(otherChannel)).isZero();
  }

  @Test
  void nonMember_isRejected_andTheCounterDoesNotMove() {
    assertThatThrownBy(() -> saveMessage.save(send(outsider, UUID.randomUUID())))
        .isInstanceOf(MemberNotFoundException.class);
    assertThat(lastSeq()).isZero();
  }

  // ---- helpers ---------------------------------------------------------------------------------

  /** Runs THREADS sends released together by one latch; returns every result. */
  private List<SavedMessage> inParallel(IntFunction<CreateMessageCommand> command)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      List<Future<SavedMessage>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        CreateMessageCommand cmd = command.apply(i);
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return saveMessage.save(cmd);
                }));
      }
      start.countDown();
      List<SavedMessage> results = new ArrayList<>();
      for (Future<SavedMessage> f : futures) {
        results.add(f.get(30, TimeUnit.SECONDS));
      }
      return results;
    }
  }

  private CreateMessageCommand send(long userId, UUID key) {
    return new CreateMessageCommand("hello", ContentType.TEXT, channel, userId, key);
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
            values (nextval('channel_seq'), 'seq-it', 'PUBLIC', 1, 'LOUNGE') returning id
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

  private long lastSeq() {
    return lastSeqOf(channel);
  }

  private long lastSeqOf(long channelId) {
    return jdbc.queryForObject("select last_seq from channel where id = ?", Long.class, channelId);
  }

  private OffsetDateTime lastMessageAt() {
    return jdbc.queryForObject(
        "select last_message_at from channel where id = ?", OffsetDateTime.class, channel);
  }

  private Long storedSeq(Long messageId) {
    return jdbc.queryForObject("select seq from message where id = ?", Long.class, messageId);
  }

  private int rowsWithKey(UUID key) {
    return jdbc.queryForObject(
        "select count(*) from message where client_msg_id = ?", Integer.class, key);
  }
}
