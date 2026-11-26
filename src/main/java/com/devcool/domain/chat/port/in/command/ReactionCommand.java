package com.devcool.domain.chat.port.in.command;

/**
 * {@code emoji} is the raw path segment; the service validates it. {@code connectionId} is the
 * actor's socket, skipped by the broadcast; null over REST.
 */
public record ReactionCommand(String connectionId, Long userId, Long messageId, String emoji) {

  /** REST: no socket to skip. */
  public ReactionCommand(Long userId, Long messageId, String emoji) {
    this(null, userId, messageId, emoji);
  }
}
