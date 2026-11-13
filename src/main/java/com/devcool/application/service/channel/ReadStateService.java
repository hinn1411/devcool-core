package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;
import com.devcool.domain.member.port.out.MemberPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One guarded UPDATE: no matched row means not a member (or no channel) → 404, no oracle. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class ReadStateService implements MarkChannelReadUseCase {

  private static final Logger log = LoggerFactory.getLogger(ReadStateService.class);

  private final MemberPort memberPort;

  public ReadStateService(MemberPort memberPort) {
    this.memberPort = memberPort;
  }

  @Override
  @Transactional
  public void markRead(MarkChannelReadCommand command) {
    if (!memberPort.advanceLastReadSeq(command.channelId(), command.userId(), command.seq())) {
      log.warn(
          "User {} marked channel {} read outside their channels",
          command.userId(),
          command.channelId());
      throw new ChannelNotFoundException(command.channelId());
    }
  }
}
