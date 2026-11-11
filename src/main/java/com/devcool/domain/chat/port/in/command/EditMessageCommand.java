package com.devcool.domain.chat.port.in.command;

/** {@code version} is the version the client last saw (optimistic lock). */
public record EditMessageCommand(Long userId, Long messageId, String content, Integer version) {}
