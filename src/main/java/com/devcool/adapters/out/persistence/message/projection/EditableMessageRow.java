package com.devcool.adapters.out.persistence.message.projection;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;

/** What an edit reads; no join, no media load. */
public record EditableMessageRow(
    Long id,
    Long channelId,
    Long seq,
    Long senderId,
    ContentType contentType,
    String content,
    Integer version,
    Instant createdTime,
    Instant editedTime) {}
