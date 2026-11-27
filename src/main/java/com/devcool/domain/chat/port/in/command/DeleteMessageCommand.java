package com.devcool.domain.chat.port.in.command;

/** {@code connectionId}: the actor's socket (skipped by the broadcast), null over REST. */
public record DeleteMessageCommand(String connectionId, Long userId, Long messageId) {

  public DeleteMessageCommand(Long userId, Long messageId) {
    this(null, userId, messageId);
  }
}
