package com.devcool.domain.channel.port.out;

import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.ChannelCursor;
import com.devcool.domain.channel.model.ChannelListPage;
import java.util.Optional;

public interface ChannelPort {
  Optional<Channel> findById(Long id);

  /** Newest activity first; null cursor = first page. */
  ChannelListPage loadChannels(Long userId, ChannelCursor cursor, Integer limit);

  boolean existById(Long id);

  /** Type and member count of the channel, or empty when the channel does not exist. */
  Optional<ChannelAccessInfo> findAccessInfo(Long id);

  /**
   * {@link #findAccessInfo} read under the channel row lock ({@code FOR NO KEY UPDATE}) until
   * commit. Member changes take it first, so checks on the count or the leader can't go stale.
   */
  Optional<ChannelAccessInfo> findAccessInfoForUpdate(Long id);

  Long save(Channel channel);

  boolean update(Channel channel);

  boolean increaseTotalMembers(Long channelId, Integer newMembers);

  /**
   * Compare-and-set: {@code leader_id = newLeaderId WHERE leader_id = expectedLeaderId}. Locks the
   * channel row until commit. False when the current leader is not {@code expectedLeaderId}.
   */
  boolean updateLeader(Long channelId, Long expectedLeaderId, Long newLeaderId);

  /**
   * Bumps and returns {@code last_seq}; locks the channel row until commit. Empty if no channel.
   */
  Optional<Long> getNextSequence(Long channelId);
}
