package com.devcool.domain.chat.port.in.command;

public record GetMessageCommand(Long userId, Long channelId, Long cursorId, Integer limit) {}
