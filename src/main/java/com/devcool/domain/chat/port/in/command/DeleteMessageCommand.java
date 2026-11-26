package com.devcool.domain.chat.port.in.command;

/** {@code connectionId} is the actor's socket, skipped by the broadcast; null over REST. */
public record DeleteMessageCommand(String connectionId, Long userId, Long messageId) {

  /** REST: no socket to skip. */
  public DeleteMessageCommand(Long userId, Long messageId) {
    this(null, userId, messageId);
  }
}
