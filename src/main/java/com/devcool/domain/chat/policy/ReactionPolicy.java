package com.devcool.domain.chat.policy;

import com.devcool.domain.chat.model.Emoji;
import java.util.Set;

/** Caps distinct emojis per message; an emoji already on it can always be added. */
public final class ReactionPolicy {

  public static final int MAX_DISTINCT_EMOJIS = 20;

  private ReactionPolicy() {}

  public static boolean mayAdd(Set<String> emojisOnMessage, Emoji emoji) {
    return emojisOnMessage.contains(emoji.value()) || emojisOnMessage.size() < MAX_DISTINCT_EMOJIS;
  }
}
