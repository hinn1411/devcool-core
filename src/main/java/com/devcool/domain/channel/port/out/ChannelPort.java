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

  /**
   * Bumps the channel's {@code last_seq} (and {@code last_message_at}) and returns the new value.
   * Locks the channel row until the caller's transaction ends, so sends to one channel get their
   * seqs in commit order (ADR-0012). Empty when the channel does not exist.
   */
  Optional<Long> getNextSequence(Long channelId);
}
