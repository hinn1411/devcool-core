package com.devcool.domain.chat.port.in.command;

import com.devcool.domain.chat.model.enums.ContentType;
import java.util.UUID;

/** {@code clientMsgId} is the sender's idempotency key: a retry with the same key saves nothing. */
public record CreateMessageCommand(
    String content, ContentType contentType, Long channelId, Long userId, UUID clientMsgId) {}
