package com.devcool.domain.channel.port.in.command;

/** "Seen up to {@code seq}". {@code connectionId}: the actor's socket, or null over REST. */
public record MarkChannelReadCommand(String connectionId, Long channelId, Long userId, long seq) {

  public MarkChannelReadCommand(Long channelId, Long userId, long seq) {
    this(null, channelId, userId, seq);
  }
}
