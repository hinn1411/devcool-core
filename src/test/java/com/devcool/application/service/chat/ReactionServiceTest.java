package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.InvalidEmojiException;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.exception.ReactionLimitException;
import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReplyTarget;
import com.devcool.domain.chat.policy.ReactionPolicy;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** P3-T09: 400 → 404 → 409 → own row only. */
@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

  private static final long CHANNEL = 1L, MESSAGE = 50L, ALICE = 10L;
  private static final String THUMBS = "👍";
  private static final Instant NOW = Instant.parse("2026-11-12T09:00:00Z");

  @Mock private ReactionPort reactionPort;
  @Mock private MessagePort messagePort;
  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private ReactionService service;

  @BeforeEach
  void setUp() {
    service =
        new ReactionService(
            reactionPort,
            messagePort,
            new ChannelAuthorizer(channelPort, memberPort),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Nested
  class React {

    @Test
    void member_addsTheirOwnRow_atNow() {
      liveMessage();
      aliceIsMember(true);

      service.react(command(THUMBS));

      verify(reactionPort).add(MESSAGE, ALICE, new Emoji(THUMBS), NOW);
    }

    @Test
    void atTheCap_aNewEmoji_is409_andWritesNothing() {
      liveMessage();
      aliceIsMember(true);
      when(reactionPort.distinctEmojis(MESSAGE))
          .thenReturn(otherEmojis(ReactionPolicy.MAX_DISTINCT_EMOJIS));

      assertThatThrownBy(() -> service.react(command(THUMBS)))
          .isInstanceOf(ReactionLimitException.class);
      verify(reactionPort, never()).add(anyLong(), anyLong(), any(), any());
    }

    @Test
    void atTheCap_anEmojiAlreadyThere_isStillAdded() {
      liveMessage();
      aliceIsMember(true);
      Set<String> full =
          new java.util.HashSet<>(otherEmojis(ReactionPolicy.MAX_DISTINCT_EMOJIS - 1));
      full.add(THUMBS);
      when(reactionPort.distinctEmojis(MESSAGE)).thenReturn(full);

      service.react(command(THUMBS));

      verify(reactionPort).add(MESSAGE, ALICE, new Emoji(THUMBS), NOW);
    }

    @Test
    void unknownMessage_is404_andWritesNothing() {
      when(messagePort.findReplyTarget(MESSAGE)).thenReturn(Optional.empty());

      assertNotFound(service::react);
    }

    @Test
    void deletedMessage_is404_andWritesNothing() {
      when(messagePort.findReplyTarget(MESSAGE))
          .thenReturn(Optional.of(new ReplyTarget(CHANNEL, NOW.minusSeconds(5))));

      assertNotFound(service::react);
    }

    @Test
    void nonMember_is404_not403_andWritesNothing() {
      liveMessage();
      aliceIsMember(false);

      assertNotFound(service::react);
    }

    @Test
    void invalidEmoji_is400_beforeAnyLookup() {
      assertThatThrownBy(() -> service.react(command("a")))
          .isInstanceOf(InvalidEmojiException.class);
      verifyNoInteractions(messagePort, memberPort, reactionPort);
    }
  }

  @Nested
  class Unreact {

    @Test
    void member_removesOnlyTheirOwnRow() {
      liveMessage();
      aliceIsMember(true);

      service.unreact(command(THUMBS));

      verify(reactionPort).remove(MESSAGE, ALICE, new Emoji(THUMBS));
    }

    @Test
    void deletedMessage_is404() {
      when(messagePort.findReplyTarget(MESSAGE))
          .thenReturn(Optional.of(new ReplyTarget(CHANNEL, NOW.minusSeconds(5))));

      assertNotFound(service::unreact);
    }

    @Test
    void nonMember_is404_andRemovesNothing() {
      liveMessage();
      aliceIsMember(false);

      assertNotFound(service::unreact);
    }

    @Test
    void invalidEmoji_is400_beforeAnyLookup() {
      assertThatThrownBy(() -> service.unreact(command("")))
          .isInstanceOf(InvalidEmojiException.class);
      verifyNoInteractions(messagePort, memberPort, reactionPort);
    }
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private void liveMessage() {
    when(messagePort.findReplyTarget(MESSAGE))
        .thenReturn(Optional.of(new ReplyTarget(CHANNEL, null)));
  }

  private void aliceIsMember(boolean member) {
    when(memberPort.existMemberOfChannelByUserId(CHANNEL, ALICE)).thenReturn(member);
  }

  private void assertNotFound(Consumer<ReactionCommand> call) {
    assertThatThrownBy(() -> call.accept(command(THUMBS)))
        .isInstanceOf(MessageNotFoundException.class);
    verify(reactionPort, never()).add(anyLong(), anyLong(), any(), any());
    verify(reactionPort, never()).remove(anyLong(), anyLong(), any());
  }

  /** {@code n} distinct emojis other than 👍 (😀, 😁, …). */
  private static Set<String> otherEmojis(int n) {
    Set<String> emojis = new java.util.HashSet<>();
    for (int i = 0; i < n; i++) {
      emojis.add(Character.toString(0x1F600 + i));
    }
    return emojis;
  }

  private static ReactionCommand command(String emoji) {
    return new ReactionCommand(ALICE, MESSAGE, emoji);
  }
}
