package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;

/** {@code MESSAGE_NEW}. */
public record MessageEvent(
    Long channelId,
    Long senderId,
    ContentType contentType,
    String content,
    Long messageId,
    Long seq,
    Instant createdTime,
    Long replyToId)
    implements RealtimeEvent {}
