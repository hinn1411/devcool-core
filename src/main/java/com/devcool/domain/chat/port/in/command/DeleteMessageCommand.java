package com.devcool.domain.chat.port.in.command;

public record DeleteMessageCommand(Long userId, Long messageId) {}
