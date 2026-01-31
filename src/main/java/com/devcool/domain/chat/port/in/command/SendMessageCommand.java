package com.devcool.domain.chat.port.in.command;

import com.devcool.domain.chat.model.enums.ContentType;

public record SendMessageCommand(
    String connectionId,
    Integer userId,
    Integer channelId,
    ContentType contentType,
    String content
) {
}
