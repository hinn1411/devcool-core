package com.devcool.domain.channel.port.out;

import com.devcool.domain.channel.model.DirectChatPair;
import java.util.Optional;

public interface DirectChatPort {
  Optional<Long> findChannelId(DirectChatPair pair);

  /** False when the pair already has a channel (a concurrent call won). */
  boolean insert(DirectChatPair pair, Long channelId);
}
