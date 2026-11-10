package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;

/** What an edit needs to check and answer, read from a message that is not deleted. */
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
