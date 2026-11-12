package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReactionSummary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ReactionPort {
  /** Inserts the row unless it exists (then a no-op, also under a concurrent insert). */
  void add(Long messageId, Long userId, Emoji emoji, Instant reactedAt);

  /** Deletes the row if present. */
  void remove(Long messageId, Long userId, Emoji emoji);

  /**
   * Per message, its emojis ordered by first reaction, with {@code reactedByMe} for {@code
   * viewerId}. One query for the whole collection; messages without reactions are absent.
   */
  Map<Long, List<ReactionSummary>> summarize(Collection<Long> messageIds, Long viewerId);
}
