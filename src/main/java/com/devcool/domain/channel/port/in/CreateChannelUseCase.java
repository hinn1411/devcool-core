package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.CreateChannelCommand;

public interface CreateChannelUseCase {
  Long createChannel(CreateChannelCommand command);
}
