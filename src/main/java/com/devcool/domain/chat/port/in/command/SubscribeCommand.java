package com.devcool.domain.chat.port.in.command;

public record SubscribeCommand(String connectionId, Long userId, Long channelId) {}
