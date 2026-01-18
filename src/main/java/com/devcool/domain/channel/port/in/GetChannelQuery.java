package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;

public interface GetChannelQuery {
  ChannelListPage getChannels(GetChannelCommand command);
}
