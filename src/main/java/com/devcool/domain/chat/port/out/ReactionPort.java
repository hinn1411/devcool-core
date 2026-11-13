package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReactionSummary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ReactionPort {
  /** No-op if the row exists, also under concurrent inserts. */
  void add(Long messageId, Long userId, Emoji emoji, Instant reactedAt);

  /** The emojis already on the message. */
  Set<String> distinctEmojis(Long messageId);

  /** Deletes the row if present. */
  void remove(Long messageId, Long userId, Emoji emoji);

  /** One query: per message, emojis by first use; messages without any are absent. */
  Map<Long, List<ReactionSummary>> summarize(Collection<Long> messageIds, Long viewerId);
}
