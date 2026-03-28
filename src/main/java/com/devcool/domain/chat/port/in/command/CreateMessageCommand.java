package com.devcool.domain.chat.port.in.command;

import com.devcool.domain.chat.model.enums.ContentType;

public record CreateMessageCommand(
    String content, ContentType contentType, Integer channelId, Integer userId) {}
