package com.devcool.domain.channel.port.out;

import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.ChannelListPage;
import java.util.Optional;

public interface ChannelPort {
  Optional<Channel> findById(Long id);

  ChannelListPage loadChannels(Long userId, Long cursorId, Integer limit);

  boolean existById(Long id);

  /** Type and member count of the channel, or empty when the channel does not exist. */
  Optional<ChannelAccessInfo> findAccessInfo(Long id);

  Long save(Channel channel);

  boolean update(Channel channel);

  boolean increaseTotalMembers(Long channelId, Integer newMembers);
}
