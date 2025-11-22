package com.devcool.domain.channel.port.out;

import com.devcool.domain.channel.model.Channel;

public interface ChannelPort {

  Integer save(Channel channel);
}
