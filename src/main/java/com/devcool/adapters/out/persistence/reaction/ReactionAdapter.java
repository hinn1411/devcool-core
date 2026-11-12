package com.devcool.adapters.out.persistence.reaction;

import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.port.out.ReactionPort;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ReactionAdapter implements ReactionPort {

  @Override
  public void add(Long messageId, Long userId, Emoji emoji, Instant reactedAt) {
    throw new UnsupportedOperationException("P3-T09 Red");
  }

  @Override
  public void remove(Long messageId, Long userId, Emoji emoji) {
    throw new UnsupportedOperationException("P3-T09 Red");
  }

  @Override
  public Map<Long, List<ReactionSummary>> summarize(Collection<Long> messageIds, Long viewerId) {
    throw new UnsupportedOperationException("P3-T09 Red");
  }
}
