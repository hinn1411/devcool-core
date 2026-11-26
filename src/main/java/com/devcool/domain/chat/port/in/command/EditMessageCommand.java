package com.devcool.domain.chat.port.in.command;

/**
 * {@code version} is the version the client last saw (optimistic lock). {@code connectionId} is the
 * actor's socket, skipped by the broadcast; null over REST.
 */
public record EditMessageCommand(
    String connectionId, Long userId, Long messageId, String content, Integer version) {

  /** REST: no socket to skip. */
  public EditMessageCommand(Long userId, Long messageId, String content, Integer version) {
    this(null, userId, messageId, content, version);
  }
}
