package com.devcool.adapters.out.persistence.reaction;

import com.devcool.adapters.out.persistence.reaction.projection.ReactionCountRow;
import com.devcool.adapters.out.persistence.reaction.repository.MessageReactionRepository;
import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.port.out.ReactionPort;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReactionAdapter implements ReactionPort {

  private final MessageReactionRepository repo;

  @Override
  public void add(Long messageId, Long userId, Emoji emoji, Instant reactedAt) {
    repo.insertIfAbsent(messageId, userId, emoji.value(), reactedAt);
  }

  @Override
  public void remove(Long messageId, Long userId, Emoji emoji) {
    repo.deleteOwn(messageId, userId, emoji.value());
  }

  @Override
  public Map<Long, List<ReactionSummary>> summarize(Collection<Long> messageIds, Long viewerId) {
    if (messageIds.isEmpty()) {
      return Map.of(); // "in ()" is invalid SQL, and there is nothing to ask anyway
    }
    return repo.countByMessage(messageIds, viewerId).stream()
        .collect(
            Collectors.groupingBy(
                ReactionCountRow::getMessageId,
                LinkedHashMap::new,
                Collectors.mapping(
                    row -> new ReactionSummary(row.getEmoji(), row.getTotal(), row.getMine()),
                    Collectors.toList())));
  }
}
