package com.devcool.adapters.out.persistence.reaction.projection;

/** One (message, emoji) group of the per-page summary. */
public interface ReactionCountRow {
  Long getMessageId();

  String getEmoji();

  Long getTotal();

  Boolean getMine();
}
