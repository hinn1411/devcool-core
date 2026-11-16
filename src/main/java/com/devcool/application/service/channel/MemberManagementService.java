package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelAction;
import com.devcool.domain.channel.port.in.LeaveChannelUseCase;
import com.devcool.domain.channel.port.in.RemoveMemberUseCase;
import com.devcool.domain.channel.port.in.TransferLeaderUseCase;
import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;
import com.devcool.domain.channel.port.in.command.RemoveMemberCommand;
import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remove a member, leave, transfer the FORUM leader (P3-T11). A FORUM always keeps exactly one
 * leader: the leader can be neither removed nor leave until the role is transferred. Every method
 * locks the channel row first, so the leader and count it checks hold until commit, and locks are
 * always taken channel row → member rows (the same order as a send).
 */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class MemberManagementService
    implements RemoveMemberUseCase, LeaveChannelUseCase, TransferLeaderUseCase {

  private static final Logger log = LoggerFactory.getLogger(MemberManagementService.class);

  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final ChannelAuthorizer channelAuthorizer;

  public MemberManagementService(
      ChannelPort channelPort, MemberPort memberPort, ChannelAuthorizer channelAuthorizer) {
    this.channelPort = channelPort;
    this.memberPort = memberPort;
    this.channelAuthorizer = channelAuthorizer;
  }

  @Override
  @Transactional
  public void removeMember(RemoveMemberCommand command) {
    Long channelId = command.channelId();
    Long targetId = command.targetUserId();
    ChannelAccessInfo channel =
        channelAuthorizer.requireAllowedForUpdate(
            channelId, command.callerId(), ChannelAction.REMOVE_MEMBER);
    if (Objects.equals(targetId, command.callerId())) {
      throw new InvalidChannelConfigException("Use leave to remove yourself");
    }
    MemberType targetRole = roleOf(channelId, targetId);
    if (targetRole == MemberType.CREATOR) {
      throw new InvalidChannelConfigException("The creator cannot be removed");
    }
    if (isForumLeader(channel, targetId)) {
      throw new InvalidChannelConfigException("The leader must transfer the role first");
    }
    deleteMemberRow(channelId, targetId);
  }

  @Override
  @Transactional
  public void leave(LeaveChannelCommand command) {
    ChannelAccessInfo channel =
        channelAuthorizer.requireAllowedForUpdate(
            command.channelId(), command.userId(), ChannelAction.LEAVE_CHANNEL);
    if (isForumLeader(channel, command.userId())) {
      throw new InvalidChannelConfigException("Transfer the leader role before leaving");
    }
    deleteMemberRow(command.channelId(), command.userId());
  }

  @Override
  @Transactional
  public void transferLeader(TransferLeaderCommand command) {
    Long channelId = command.channelId();
    Long callerId = command.callerId();
    Long newLeaderId = command.newLeaderId();
    ChannelAccessInfo channel =
        channelAuthorizer.requireAllowedForUpdate(
            channelId, callerId, ChannelAction.TRANSFER_LEADER);
    if (!Objects.equals(channel.leaderId(), callerId)) {
      throw new ForbiddenException("Only the current leader can transfer the role");
    }
    if (Objects.equals(newLeaderId, callerId)) {
      throw new InvalidChannelConfigException("You already are the leader");
    }
    MemberType newLeaderRole = roleOf(channelId, newLeaderId);
    MemberType callerRole = roleOf(channelId, callerId);
    // The channel lock makes this CAS a backstop; it still guards a direct SQL change of leader_id
    if (!channelPort.updateLeader(channelId, callerId, newLeaderId)) {
      log.warn("User {} lost a concurrent leader transfer in channel {}", callerId, channelId);
      throw new ForbiddenException("Only the current leader can transfer the role");
    }
    // A CREATOR row stays CREATOR on either side; only LEADER/MEMBER rows swap
    if (callerRole == MemberType.LEADER) {
      setRole(channelId, callerId, MemberType.MEMBER);
    }
    if (newLeaderRole == MemberType.MEMBER) {
      setRole(channelId, newLeaderId, MemberType.LEADER);
    }
  }

  private MemberType roleOf(Long channelId, Long userId) {
    return memberPort
        .findRoleOfMember(channelId, userId)
        .orElseThrow(() -> new MemberNotFoundException(userId));
  }

  /** No row matched: throwing rolls back the leader_id change made earlier in this transaction. */
  private void setRole(Long channelId, Long userId, MemberType role) {
    if (!memberPort.updateRole(channelId, userId, role)) {
      throw new MemberNotFoundException(userId);
    }
  }

  private static boolean isForumLeader(ChannelAccessInfo channel, Long userId) {
    return channel.channelType() == ChannelType.FORUM && Objects.equals(channel.leaderId(), userId);
  }

  /** Delete first: only the transaction that really deleted the row lowers the count. */
  private void deleteMemberRow(Long channelId, Long userId) {
    if (!memberPort.removeMember(channelId, userId)) {
      throw new MemberNotFoundException(userId);
    }
    channelPort.increaseTotalMembers(channelId, -1);
  }
}
