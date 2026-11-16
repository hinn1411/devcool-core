package com.devcool.adapters.out.persistence.channel;

import com.devcool.domain.channel.model.DirectChatPair;
import com.devcool.domain.channel.port.out.DirectChatPort;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class DirectChatAdapter implements DirectChatPort {

  @Override
  public Optional<Long> findChannelId(DirectChatPair pair) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean insert(DirectChatPair pair, Long channelId) {
    throw new UnsupportedOperationException();
  }
}
