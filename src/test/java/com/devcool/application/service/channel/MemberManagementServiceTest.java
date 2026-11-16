package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Service-only rules; roles are in ChannelPermissionPolicyTest. */
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

  private void assertNothingWritten() {
    verify(memberPort, never()).removeMember(anyLong(), anyLong());
    verify(memberPort, never()).updateRole(anyLong(), anyLong(), any());
    verify(channelPort, never()).increaseTotalMembers(anyLong(), anyInt());
  }

  @Nested
  class Remove {

    private void remove(long targetId) {
      newService().removeMember(new RemoveMemberCommand(CHANNEL_ID, CALLER_ID, targetId));
    }

    @Test
    void deletesRowThenDecrementsCount() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(memberPort.removeMember(CHANNEL_ID, TARGET_ID)).thenReturn(true);

      remove(TARGET_ID);

      InOrder order = inOrder(memberPort, channelPort);
      order.verify(memberPort).removeMember(CHANNEL_ID, TARGET_ID);
      order.verify(channelPort).increaseTotalMembers(CHANNEL_ID, -1);
    }

    @Test
    void self_isRejected() {
      channelIs(ChannelType.LOUNGE, null);
      roleOf(CALLER_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> remove(CALLER_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void creator_isRejected() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.CREATOR);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void forumLeader_isRejected() {
      channelIs(ChannelType.FORUM, TARGET_ID);
      roleOf(CALLER_ID, MemberType.CREATOR);
      roleOf(TARGET_ID, MemberType.LEADER);

      assertThatThrownBy(() -> remove(TARGET_ID)).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }

    @Test
    void rowAlreadyGone_doesNotDecrement() {
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

    @Test
    void deletesRowThenDecrementsCount() {
      channelIs(ChannelType.FORUM, OTHER_ID);
      roleOf(CALLER_ID, MemberType.MEMBER);
      when(memberPort.removeMember(CHANNEL_ID, CALLER_ID)).thenReturn(true);

      leave();

      verify(channelPort).increaseTotalMembers(CHANNEL_ID, -1);
    }

    @Test
    void forumLeader_mustTransferFirst() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);

      assertThatThrownBy(this::leave).isInstanceOf(InvalidChannelConfigException.class);
      assertNothingWritten();
    }
  }

  @Nested
  class Transfer {

    private void transfer(long newLeaderId) {
      newService().transferLeader(new TransferLeaderCommand(CHANNEL_ID, CALLER_ID, newLeaderId));
    }

    private void transferSucceeds(MemberType callerRole) {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, callerRole);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(true);
      when(memberPort.updateRole(anyLong(), anyLong(), any())).thenReturn(true);
    }

    @Test
    void leaderBecomesMember_targetBecomesLeader() {
      transferSucceeds(MemberType.LEADER);

      transfer(TARGET_ID);

      verify(memberPort).updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER);
      verify(memberPort).updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER);
    }

    @Test
    void creatorLeader_staysCreator() {
      transferSucceeds(MemberType.CREATOR);

      transfer(TARGET_ID);

      verify(memberPort, never()).updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER);
      verify(memberPort).updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER);
    }

    @Test
    void creatorWhoIsNotTheLeader_isForbidden() {
      channelIs(ChannelType.FORUM, OTHER_ID);
      roleOf(CALLER_ID, MemberType.CREATOR);

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
      when(memberPort.findRoleOfMember(CHANNEL_ID, TARGET_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
      assertNothingWritten();
    }

    @Test
    void newLeaderRowGone_throwsSoTheTransactionRollsBack() {
      channelIs(ChannelType.FORUM, CALLER_ID);
      roleOf(CALLER_ID, MemberType.LEADER);
      roleOf(TARGET_ID, MemberType.MEMBER);
      when(channelPort.updateLeader(CHANNEL_ID, CALLER_ID, TARGET_ID)).thenReturn(true);
      when(memberPort.updateRole(CHANNEL_ID, CALLER_ID, MemberType.MEMBER)).thenReturn(true);
      when(memberPort.updateRole(CHANNEL_ID, TARGET_ID, MemberType.LEADER)).thenReturn(false);

      assertThatThrownBy(() -> transfer(TARGET_ID)).isInstanceOf(MemberNotFoundException.class);
    }
  }
}
