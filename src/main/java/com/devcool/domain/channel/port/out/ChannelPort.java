package com.devcool.domain.channel.port.out;

import com.devcool.domain.channel.model.Channel;

import java.util.Optional;

public interface ChannelPort {
  Optional<Channel> findById(Integer id);
  Integer save(Channel channel);
  boolean update(Channel channel);
}
