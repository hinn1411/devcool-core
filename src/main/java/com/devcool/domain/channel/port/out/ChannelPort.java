package com.devcool.domain.channel.port.out;

import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelListPage;

import java.util.Optional;

public interface ChannelPort {
  Optional<Channel> findById(Integer id);

  ChannelListPage loadChannels(Integer userId, Integer cursorId, Integer limit);
  boolean existById(Integer id);
  Integer save(Channel channel);
  boolean update(Channel channel);
  boolean increaseTotalMembers(Integer channelId, Integer newMembers);
}
