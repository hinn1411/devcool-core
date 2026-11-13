package com.devcool.application.service.channel;

import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import com.devcool.domain.member.port.out.MemberPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;

@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class ReadStateService implements MarkChannelReadUseCase {

  private final MemberPort memberPort;

  public ReadStateService(MemberPort memberPort) {
    this.memberPort = memberPort;
  }

  @Override
  public void markRead(MarkChannelReadCommand command) {
    throw new UnsupportedOperationException();
  }
}
