package com.devcool.application.service.realtime;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.realtime.port.in.WsSubscribeUseCase;
import com.devcool.domain.realtime.port.in.command.SubscribeCommand;
import com.devcool.domain.realtime.port.out.ConnectionRegistryPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WsSubscribeService implements WsSubscribeUseCase {

  private static final Logger log = LoggerFactory.getLogger(WsSubscribeService.class);
  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final ConnectionRegistryPort connectionRegistryPort;

  @Override
  @Transactional
  public void subscribe(SubscribeCommand command) {
    // Validate channel status later
    Channel channel = channelPort.findById(command.channelId())
        .orElseThrow(() -> {
          log.warn("Channel id {} does not exist", command.channelId());
          throw new ChannelNotFoundException(command.channelId());
        });
    // Validate member permission later
    Member member = memberPort.findMemberOfChannelByUserId(command.channelId(), command.userId())
        .orElseThrow(() -> {
          log.info("User id {} does not exist in channel id {}", command.userId(), command.channelId());
          throw new MemberNotFoundException(command.userId());
        });

    connectionRegistryPort.subscribe(command.connectionId(), command.channelId());

  }
}
