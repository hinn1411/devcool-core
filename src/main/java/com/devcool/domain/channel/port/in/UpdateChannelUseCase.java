package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;

public interface UpdateChannelUseCase {
  boolean updateChannel(Integer channelId, UpdateChannelCommand command);
}
