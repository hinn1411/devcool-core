package com.devcool.domain.chat.port.in.command;

/** {@code emoji}: raw, validated by the service. {@code connectionId}: actor's socket, or null. */
public record ReactionCommand(String connectionId, Long userId, Long messageId, String emoji) {

  public ReactionCommand(Long userId, Long messageId, String emoji) {
    this(null, userId, messageId, emoji);
  }
}
