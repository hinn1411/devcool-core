package com.devcool.domain.realtime.port.in.command;

import com.devcool.domain.model.enums.ContentType;

public record SendMessageCommand(
    String connectionId,
    Integer userId,
    Integer channelId,
    ContentType contentType,
    String content
) {
}
