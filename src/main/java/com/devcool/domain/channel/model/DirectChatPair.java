package com.devcool.domain.channel.model;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;

/** The unordered pair of a direct chat, stored low id first so A→B and B→A share one key. */
public record DirectChatPair(Long lowUserId, Long highUserId) {

  public static DirectChatPair of(Long userId, Long otherUserId) {
    int order = userId.compareTo(otherUserId);
    if (order == 0) {
      throw new InvalidChannelConfigException("Cannot open a direct chat with yourself");
    }
    return order < 0
        ? new DirectChatPair(userId, otherUserId)
        : new DirectChatPair(otherUserId, userId);
  }
}
