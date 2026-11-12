package com.devcool.domain.chat.port.in.command;

/** {@code emoji} is the raw path segment; the service validates it. */
public record ReactionCommand(Long userId, Long messageId, String emoji) {}
