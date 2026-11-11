package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;

/** A non-deleted message, as an edit reads it. */
public record EditableMessage(
    Long id,
    Long channelId,
    Long seq,
    Long senderId,
    ContentType contentType,
    String content,
    int version,
    Instant createdTime,
    Instant editedTime) {}
