package com.devcool.application.service.chat;

import com.devcool.domain.chat.port.in.WsUnsubscribeUseCase;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the port; it is a shared stateless bean, not owned state")
public class WsUnsubscribeService implements WsUnsubscribeUseCase {

  private final ConnectionRegistryPort connectionRegistryPort;

  /** No membership check: it only shrinks this connection's own subscriptions. */
  @Override
  public void unsubscribe(UnsubscribeCommand command) {
    connectionRegistryPort.unsubscribe(command.connectionId(), command.channelId());
  }
}
