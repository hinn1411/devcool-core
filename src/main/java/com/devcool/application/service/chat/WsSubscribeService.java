package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.port.in.WsSubscribeUseCase;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WsSubscribeService implements WsSubscribeUseCase {

  private final ChannelAuthorizer channelAuthorizer;
  private final ConnectionRegistryPort connectionRegistryPort;

  @Override
  @Transactional
  public void subscribe(SubscribeCommand command) {
    // Validate channel status and member permission later
    channelAuthorizer.requireMember(command.channelId(), command.userId());

    connectionRegistryPort.subscribe(command.connectionId(), command.channelId());
  }
}
