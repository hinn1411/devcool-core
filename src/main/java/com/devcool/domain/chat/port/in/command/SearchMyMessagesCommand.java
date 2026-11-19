package com.devcool.domain.chat.port.in.command;

/** {@code beforeId} null = newest page. */
public record SearchMyMessagesCommand(Long userId, String query, Long beforeId, Integer limit) {}
