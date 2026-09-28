package com.devcool.application.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.port.out.UserPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

  @Mock private ChannelPort channelPort;
  @Mock private MemberPort memberPort;
  @Mock private UserPort userPort;

  private ChannelService newService() {
    return new ChannelService(List.of(), channelPort, memberPort, userPort);
  }

  private UpdateChannelCommand command() {
    return new UpdateChannelCommand("new-name", null, null, ChannelType.FORUM);
  }

  @Test
  void updateChannel_existingChannel_updatesWithoutLoadingAggregate() {
    ChannelService service = newService();
    when(channelPort.update(any())).thenReturn(true);

    boolean result = service.updateChannel(42, command());

    assertThat(result).isTrue();
    verify(channelPort).update(any());
    verify(channelPort, never()).findById(anyInt());
  }

  @Test
  void updateChannel_missingChannel_throwsChannelNotFound() {
    ChannelService service = newService();
    when(channelPort.update(any())).thenReturn(false);

    assertThatThrownBy(() -> service.updateChannel(42, command()))
        .isInstanceOf(ChannelNotFoundException.class);

    verify(channelPort, never()).findById(anyInt());
  }
}
