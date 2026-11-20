package com.devcool.application.service.chat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WsUnsubscribeServiceTest {

  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @InjectMocks private WsUnsubscribeService service;

  /** No membership check: it only shrinks this connection's own subscriptions (a no-op if none). */
  @Test
  void dropsOnlyThisConnectionFromTheChannel() {
    service.unsubscribe(new UnsubscribeCommand("conn-1", 42L));

    verify(connectionRegistryPort).unsubscribe("conn-1", 42L);
    verifyNoMoreInteractions(connectionRegistryPort);
  }
}
