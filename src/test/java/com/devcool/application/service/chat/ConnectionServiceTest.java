package com.devcool.application.service.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;
import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.common.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

  @Mock private ChannelAuthorizer channelAuthorizer;
  @Mock private ConnectionRegistryPort connectionRegistryPort;
  @InjectMocks private ConnectionService service;

  @Test
  void connect_registersTheConnectionForItsUser() {
    service.connect(new ConnectCommand("conn-1", 7L));

    verify(connectionRegistryPort).registerConnection("conn-1", 7L);
  }

  @Test
  void disconnect_removesTheConnection() {
    service.disconnect(new DisconnectCommand("conn-1"));

    verify(connectionRegistryPort).removeConnection("conn-1");
  }

  @Test
  void subscribe_member_addsTheConnectionToTheChannel() {
    service.subscribe(new SubscribeCommand("conn-1", 7L, 42L));

    verify(channelAuthorizer).requireMember(42L, 7L);
    verify(connectionRegistryPort).subscribe("conn-1", 42L);
  }

  @Test
  void subscribe_nonMember_isRejectedAndSubscribesNothing() {
    doThrow(new ForbiddenException("Not a member")).when(channelAuthorizer).requireMember(42L, 7L);

    assertThatThrownBy(() -> service.subscribe(new SubscribeCommand("conn-1", 7L, 42L)))
        .isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(connectionRegistryPort);
  }

  @Test
  void unsubscribe_dropsOnlyThisConnectionFromTheChannel() {
    service.unsubscribe(new UnsubscribeCommand("conn-1", 42L));

    verify(connectionRegistryPort).unsubscribe("conn-1", 42L);
    verifyNoMoreInteractions(connectionRegistryPort);
    verifyNoInteractions(channelAuthorizer);
  }
}
