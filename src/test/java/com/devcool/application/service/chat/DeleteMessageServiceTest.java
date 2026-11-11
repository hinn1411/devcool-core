package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.model.DeletableMessage;
import com.devcool.domain.chat.port.in.command.DeleteMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Delete rules (P3-T07): sender or group admin, 404 without oracle, idempotent. */
@ExtendWith(MockitoExtension.class)
class DeleteMessageServiceTest {

  private static final long MESSAGE = 500L, CHANNEL = 1L, ALICE = 10L, BOB = 11L;
  private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
  private static final Instant DELETED_BEFORE = NOW.minusSeconds(600);

  @Mock private MessagePort messagePort;
  @Mock private MemberPort memberPort;
  @Mock private ChannelPort channelPort;

  private DeleteMessageService service;

  @BeforeEach
  void setUp() {
    service =
        new DeleteMessageService(
            messagePort, memberPort, channelPort, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  // ---- 1. sender -------------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(ChannelType.class)
  void sender_deletesOwnMessage_inAnyChannelType(ChannelType type) {
    aliceMessage(null);
    caller(ALICE, MemberType.MEMBER, type);
    when(messagePort.markDeleted(MESSAGE, NOW)).thenReturn(true);

    service.delete(delete(ALICE));

    verify(messagePort).markDeleted(MESSAGE, NOW);
  }

  // ---- 2. group admin --------------------------------------------------------------------------

  @ParameterizedTest
  // LEADER exists only in FORUM (ForumCreationStrategy), so LOUNGE has no LEADER row.
  @CsvSource({"LOUNGE,CREATOR", "FORUM,CREATOR", "FORUM,LEADER"})
  void creatorOrLeader_ofGroupChannel_deletesOthersMessage(ChannelType type, MemberType role) {
    aliceMessage(null);
    caller(BOB, role, type);
    when(messagePort.markDeleted(MESSAGE, NOW)).thenReturn(true);

    service.delete(delete(BOB));

    verify(messagePort).markDeleted(MESSAGE, NOW);
  }

  // ---- 3. authorization ------------------------------------------------------------------------

  @ParameterizedTest
  @EnumSource(ChannelType.class)
  void plainMember_cannotDeleteOthersMessage(ChannelType type) {
    aliceMessage(null);
    caller(BOB, MemberType.MEMBER, type);

    assertThatThrownBy(() -> service.delete(delete(BOB))).isInstanceOf(ForbiddenException.class);
    neverMarked();
  }

  @Test
  void privateChatCreator_cannotDeleteTheOtherPersonsMessage() {
    // A DM's opener is stored as CREATOR, but is not a moderator.
    aliceMessage(null);
    caller(BOB, MemberType.CREATOR, ChannelType.PRIVATE_CHAT);

    assertThatThrownBy(() -> service.delete(delete(BOB))).isInstanceOf(ForbiddenException.class);
    neverMarked();
  }

  @Test
  void nonMember_isNotFound() {
    aliceMessage(null);
    when(memberPort.findRoleOfMember(CHANNEL, BOB)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(delete(BOB)))
        .isInstanceOf(MessageNotFoundException.class);
    neverMarked();
  }

  @Test
  void nonMember_onAlreadyDeletedMessage_isStillNotFound() {
    // The idempotent no-op must not tell an outsider that the id exists.
    aliceMessage(DELETED_BEFORE);
    when(memberPort.findRoleOfMember(CHANNEL, BOB)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(delete(BOB)))
        .isInstanceOf(MessageNotFoundException.class);
  }

  @Test
  void unknownMessage_isNotFound() {
    when(messagePort.findForDelete(MESSAGE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(delete(ALICE)))
        .isInstanceOf(MessageNotFoundException.class);
    neverMarked();
  }

  @Test
  void plainMember_onAlreadyDeletedMessage_isStillForbidden() {
    aliceMessage(DELETED_BEFORE);
    caller(BOB, MemberType.MEMBER, ChannelType.LOUNGE);

    assertThatThrownBy(() -> service.delete(delete(BOB))).isInstanceOf(ForbiddenException.class);
  }

  // ---- 4. idempotent ---------------------------------------------------------------------------

  @Test
  void alreadyDeleted_isNoOp_keepsTheFirstTimestamp() {
    aliceMessage(DELETED_BEFORE);
    caller(ALICE, MemberType.MEMBER, ChannelType.LOUNGE);

    assertThatCode(() -> service.delete(delete(ALICE))).doesNotThrowAnyException();
    neverMarked();
  }

  @Test
  void lostRaceToAnotherDelete_isStillSuccess() {
    // Read as live, but a concurrent delete committed first: 0 rows, same answer.
    aliceMessage(null);
    caller(ALICE, MemberType.MEMBER, ChannelType.LOUNGE);
    when(messagePort.markDeleted(MESSAGE, NOW)).thenReturn(false);

    assertThatCode(() -> service.delete(delete(ALICE))).doesNotThrowAnyException();
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private void aliceMessage(Instant deletedTime) {
    when(messagePort.findForDelete(MESSAGE))
        .thenReturn(Optional.of(new DeletableMessage(MESSAGE, CHANNEL, ALICE, deletedTime)));
  }

  /** Lenient channel stub: a sender may be allowed without the service reading the type. */
  private void caller(long userId, MemberType role, ChannelType type) {
    when(memberPort.findRoleOfMember(CHANNEL, userId)).thenReturn(Optional.of(role));
    lenient()
        .when(channelPort.findAccessInfo(CHANNEL))
        .thenReturn(Optional.of(new ChannelAccessInfo(type, 2)));
  }

  private static DeleteMessageCommand delete(long userId) {
    return new DeleteMessageCommand(userId, MESSAGE);
  }

  private void neverMarked() {
    verify(messagePort, never()).markDeleted(anyLong(), any());
  }
}
