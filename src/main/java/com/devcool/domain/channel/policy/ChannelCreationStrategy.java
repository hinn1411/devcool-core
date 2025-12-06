package com.devcool.domain.channel.policy;

import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;

public interface ChannelCreationStrategy {
  ChannelType getSupportedType();

  Integer createChannel(CreateChannelCommand command);
}
