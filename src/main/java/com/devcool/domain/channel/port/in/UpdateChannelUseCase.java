package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;

public interface UpdateChannelUseCase {
  boolean updateChannel(Long channelId, UpdateChannelCommand command);

  boolean addMember(Long channelId, AddMembersCommand command);
}
