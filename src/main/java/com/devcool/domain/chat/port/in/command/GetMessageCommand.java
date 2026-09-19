package com.devcool.domain.chat.port.in.command;

public record GetMessageCommand(
    Integer userId, Integer channelId, Integer cursorId, Integer limit) {}
