package com.devcool.adapters.out.persistence.channel;

import com.devcool.adapters.out.persistence.channel.repository.DirectChatRepository;
import com.devcool.domain.channel.model.DirectChatPair;
import com.devcool.domain.channel.port.out.DirectChatPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the repository; a shared stateless bean, not owned state")
public class DirectChatAdapter implements DirectChatPort {
  private final DirectChatRepository repo;

  @Override
  public Optional<Long> findChannelId(DirectChatPair pair) {
    return repo.findChannelId(pair.lowUserId(), pair.highUserId());
  }

  /**
   * {@code ON CONFLICT DO NOTHING}: a lost race returns false without aborting the transaction, and
   * under READ COMMITTED the next statement already sees the winner's committed row.
   */
  @Override
  public boolean insert(DirectChatPair pair, Long channelId) {
    return repo.insertIfAbsent(pair.lowUserId(), pair.highUserId(), channelId) == 1;
  }
}
