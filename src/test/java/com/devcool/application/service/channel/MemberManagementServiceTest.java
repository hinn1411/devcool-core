package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;
import com.devcool.domain.channel.port.in.command.RemoveMemberCommand;
import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Rules agreed in docs/journal/briefs/P3-T11.md; the real policy and authorizer run. */
@ExtendWith(MockitoExtension.class)
class MemberManagementServiceTest {

  private static final long CHANNEL_ID = 42L;
  private static final long CALLER_ID = 7L;
  private static final long TARGET_ID = 9L;
  private static final long OTHER_ID = 11L;

  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;

  private MemberManagementService newService() {
    return new MemberManagementService(
        channelPort, memberPort, new ChannelAuthorizer(channelPort, memberPort));
  }

  private void channelIs(ChannelType type, Long leaderId) {
    when(channelPort.findAccessInfoForUpdate(CHANNEL_ID))
        .thenReturn(Optional.of(new ChannelAccessInfo(type, 3, leaderId)));
  }

  private void roleOf(long userId, MemberType role) {
    when(memberPort.findRoleOfMember(CHANNEL_ID, userId)).thenReturn(Optional.of(role));
  }

  private void notAMember(long userId) {
    when(memberPort.findRoleOfMember(CHANNEL_ID, userId)).thenReturn(Optional.empty());
  }

  private void rolesUpdate() {
    lenient().when(memberPort.updateRole(anyLong(), anyLong(), any())).thenReturn(true);
  }

  private void assertNothingWritten() {
    verify(memberPort, never()).removeMember(anyLong(), anyLong());
    verify(memberPort, never()).updateRole(anyLong(), anyLong(), any());
    verify(channelPort, never()).increaseTotalMembers(anyLong(), anyInt());
    verify(channelPort, never()).updateLeader(anyLong(), anyLong(), anyLong());
  }

  @Nested
  class Remove {

    private void remove(long targetId) {
      newService().removeMember(new RemoveMemberCommand(CHANNEL_ID, CALLER_ID, targetId));
    }

    @ParameterizedTest(name = "{0} {1} removes a MEMBER")
    @CsvSource({"LOUNGE, CREATOR", "FORUM, CREATOR", "FORUM, LEADER"})
    void adminRemovesMember_deletesRowThenDecrementsCount(ChannelType type, MemberType role) {
      channelIs(type, type == ChannelType.FORUM ? OTHER_ID : null);
      roleOf(CALLER_ID, role);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(memberPort.removeMember(CHANNEL_ID, TARGET_ID)).thenReturn(true);

      remove(TARGET_ID);

      InOrder order = inOrder(memberPort, channelPort);
      order.verify(memberPort).removeMember(CHANNEL_ID, TARGET_ID);
      order.verify(channelPort).increaseTotalMembers(CHANNEL_ID, -1);
    }

    @ParameterizedTest(name = "{0} {1} may not remove → 403")
    @CsvSource({"LOUNGE, MEMBER", "FORUM, MEMBER"})
    void nonAdminCaller_isForbidden(ChannelType type, MemberType role) {
      channelIs(type, null);
      roleOf(CALLER_ID, role);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(ForbiddenException.class);
      assertNothingWritten();
    }

    @Test
    void privateChat_isUnsupported() {
      channelIs(ChannelType.PRIVATE_CHAT, null);
      roleOf(CALLER_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void removingSelf_isRejected() {
      channelIs(ChannelType.LOUNGE, null);
      roleOf(CALLER_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> remove(CALLER_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void removingTheCreator_isRejected() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void removingTheForumLeader_isRejected_soTheForumKeepsALeader() {
      channelIs(ChannelType.FORUM, TARGET_ID);
      roleOf(CALLER_ID, MemberType.CREATOR);
      roleOf(TARGET_ID, MemberType.LEADER);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void targetNotAMember_isMemberNotFound() {
      channelIs(ChannelType.LOUNGE, null);
      roleOf(CALLER_ID, MemberType.CREATOR);
      notAMember(TARGET_ID);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
      assertNothingWritten();
    }

    @Test
    void callerNotAMember_isMemberNotFound() {
      channelIs(ChannelType.LOUNGE, null);
      notAMember(CALLER_ID);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
      assertNothingWritten();
    }

    @Test
    void unknownChannel_isNotFound() {
      when(channelPort.findAccessInfoForUpdate(CHANNEL_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(ChannelNotFoundException.class);
      assertNothingWritten();
    }

    @Test
    void rowAlreadyGone_concurrentRemove_doesNotDecrementTwice() {
      channelIs(ChannelType.LOUNGE, null);
      roleOf(CALLER_ID, MemberType.CREATOR);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(memberPort.removeMember(CHANNEL_ID, TARGET_ID)).thenReturn(false);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
      verify(channelPort, never()).increaseTotalMembers(anyLong(), anyInt());
    }
  }

  @Nested
  class Leave {

    private void leave() {
      newService().leave(new LeaveChannelCommand(CHANNEL_ID, CALLER_ID));
    }

    @ParameterizedTest(name = "{0} {1} leaves")
    @CsvSource({"LOUNGE, MEMBER", "LOUNGE, CREATOR", "FORUM, MEMBER", "FORUM, CREATOR"})
    void member_leaves_rowDeletedThenCountDecremented(ChannelType type, MemberType role) {
      channelIs(type, type == ChannelType.FORUM ? OTHER_ID : null);
      roleOf(CALLER_ID, role);
      when(memberPort.removeMember(CHANNEL_ID, CALLER_ID)).thenReturn(true);

      leave();

      InOrder order = inOrder(memberPort, channelPort);
      order.verify(memberPort).removeMember(CHANNEL_ID, CALLER_ID);
      order.verify(channelPort).increaseTotalMembers(CHANNEL_ID, -1);
    }

    @ParameterizedTest(name = "forum leader holding {0} must transfer first")
    @CsvSource({"LEADER", "CREATOR"})
    void forumLeader_mustTransferFirst(MemberType role) {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, role);

      assertThatThrownBy(this::leave).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void privateChat_isUnsupported() {
      channelIs(ChannelType.PRIVATE_CHAT, null);
      roleOf(CALLER_ID, MemberType.MEMBER);

      assertThatThrownBy(this::leave).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void nonMember_isMemberNotFound() {
      channelIs(ChannelType.LOUNGE, null);
      notAMember(CALLER_ID);

      assertThatThrownBy(this::leave).isInstanceOf(MemberNotFoundException.class);
      assertNothingWritten();
    }

    @Test
    void unknownChannel_isNotFound() {
      when(channelPort.findAccessInfoForUpdate(CHANNEL_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(this::leave).isInstanceOf(ChannelNotFoundException.class);
      assertNothingWritten();
    }
  }

  @Nested
  class Transfer {

    private void transfer(long newLeaderId) {
      newService().transferLeader(new TransferLeaderCommand(CHANNEL_ID, CALLER_ID, newLeaderId));
    }

    @Test
    void leaderRow_becomesMember_targetBecomesLeader() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(true);
      rolesUpdate();

      transfer(TARGET_ID);

      verify(channelPort).updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID);
      verify(memberPort).updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER);
      verify(memberPort).updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER);
    }

    @Test
    void creatorLeader_staysCreator() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.CREATOR);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(true);
      rolesUpdate();

      transfer(TARGET_ID);

      verify(memberPort, never()).updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER);
      verify(memberPort).updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER);
    }

    @Test
    void toTheCreator_keepsTheCreatorRow() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.CREATOR);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(true);
      rolesUpdate();

      transfer(TARGET_ID);

      verify(memberPort).updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER);
      verify(memberPort, never()).updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER);
    }

    @Test
    void newLeaderRowGone_throwsSoTheLeaderIdChangeRollsBack() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(true);
      when(memberPort.updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER)).thenReturn(true);
      when(memberPort.updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER)).thenReturn(false);

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void creatorWhoIsNotTheLeader_isForbidden() {
      channelIs(ChannelType.FORUM, OTHER_ID);
      roleOf(CALLER_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(ForbiddenException.class);
      assertNothingWritten();
    }

    @Test
    void forumMember_isForbidden() {
      channelIs(ChannelType.FORUM, OTHER_ID);
      roleOf(CALLER_ID, MemberType.MEMBER);

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(ForbiddenException.class);
      assertNothingWritten();
    }

    @Test
    void toSelf_isRejected() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);

      assertThatThrownBy(() -> transfer(CALLER_ID))
          .isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void toNonMember_isMemberNotFound() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      notAMember(TARGET_ID);

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
      assertNothingWritten();
    }

    @ParameterizedTest(name = "{0} has no leader → 400")
    @CsvSource({"LOUNGE", "PRIVATE_CHAT"})
    void nonForum_isUnsupported(ChannelType type) {
      channelIs(type, null);
      roleOf(CALLER_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> transfer(TARGET_ID))
          .isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void lostTheCompareAndSet_concurrentTransfer_isForbiddenAndChangesNoRole() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(false);

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(ForbiddenException.class);
      verify(memberPort, never()).updateRole(anyLong(), anyLong(), any());
    }
  }
}
