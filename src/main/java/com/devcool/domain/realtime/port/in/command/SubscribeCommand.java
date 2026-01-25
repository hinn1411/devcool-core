package com.devcool.domain.realtime.port.in.command;

public record SubscribeCommand(
    String connectionId,
    Integer userId,
    Integer channelId
) {
}
