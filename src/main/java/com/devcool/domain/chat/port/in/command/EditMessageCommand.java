package com.devcool.domain.chat.port.in.command;

/** {@code version}: the one the client last saw. {@code connectionId}: actor's socket, or null. */
public record EditMessageCommand(
    String connectionId, Long userId, Long messageId, String content, Integer version) {

  public EditMessageCommand(Long userId, Long messageId, String content, Integer version) {
    this(null, userId, messageId, content, version);
  }
}
