package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.port.in.ChannelSubscriptionUseCase;
import com.devcool.domain.chat.port.in.ConnectionLifecycleUseCase;
import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ConnectionService implements ConnectionLifecycleUseCase, ChannelSubscriptionUseCase {

  private final ChannelAuthorizer channelAuthorizer;
  private final ConnectionRegistryPort connectionRegistryPort;

  @Override
  public void connect(ConnectCommand command) {
    connectionRegistryPort.registerConnection(command.connectionId(), command.userId());
  }

  @Override
  public void disconnect(DisconnectCommand command) {
    connectionRegistryPort.removeConnection(command.connectionId());
  }

  @Override
  @Transactional
  public void subscribe(SubscribeCommand command) {
    // Validate channel status later
    channelAuthorizer.requireMember(command.channelId(), command.userId());
    connectionRegistryPort.subscribe(command.connectionId(), command.channelId());
  }

  @Override
  public void unsubscribe(UnsubscribeCommand command) {
    connectionRegistryPort.unsubscribe(command.connectionId(), command.channelId());
  }
}
