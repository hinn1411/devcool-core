package com.devcool.domain.channel.model;

/** The unordered pair of a direct chat, stored low id first so A→B and B→A share one key. */
public record DirectChatPair(Long lowUserId, Long highUserId) {

  public static DirectChatPair of(Long userId, Long otherUserId) {
    throw new UnsupportedOperationException();
  }
}
