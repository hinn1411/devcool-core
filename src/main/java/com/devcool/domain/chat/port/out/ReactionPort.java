package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReactionSummary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ReactionPort {
  /** True if this call inserted the row (a no-op otherwise, also under races). */
  boolean add(Long messageId, Long userId, Emoji emoji, Instant reactedAt);

  /** The emojis already on the message. */
  Set<String> distinctEmojis(Long messageId);

  /** True if this call deleted the row. */
  boolean remove(Long messageId, Long userId, Emoji emoji);

  /** One query: per message, emojis by first use; messages without any are absent. */
  Map<Long, List<ReactionSummary>> summarize(Collection<Long> messageIds, Long viewerId);
}
