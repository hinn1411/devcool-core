package com.devcool.domain.channel.port.in.command;

/**
 * "I have seen everything up to {@code seq}" from {@code userId} in {@code channelId}. {@code
 * connectionId} is the actor's socket, skipped by the broadcast; null over REST.
 */
public record MarkChannelReadCommand(String connectionId, Long channelId, Long userId, long seq) {

  /** REST: no socket to skip. */
  public MarkChannelReadCommand(Long channelId, Long userId, long seq) {
    this(null, channelId, userId, seq);
  }
}
