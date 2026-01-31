package com.devcool.domain.chat.port.in.command;

public record SubscribeCommand(
    String connectionId,
    Integer userId,
    Integer channelId
) {
}
