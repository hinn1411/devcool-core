package com.devcool.application.service.channel;

import com.devcool.domain.channel.port.in.LeaveChannelUseCase;
import com.devcool.domain.channel.port.in.RemoveMemberUseCase;
import com.devcool.domain.channel.port.in.TransferLeaderUseCase;
import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;
import com.devcool.domain.channel.port.in.command.RemoveMemberCommand;
import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.port.out.MemberPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;

/** Remove a member, leave, transfer the FORUM leader (P3-T11). */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class MemberManagementService
    implements RemoveMemberUseCase, LeaveChannelUseCase, TransferLeaderUseCase {

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
  public void removeMember(RemoveMemberCommand command) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void leave(LeaveChannelCommand command) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void transferLeader(TransferLeaderCommand command) {
    throw new UnsupportedOperationException();
  }
}
