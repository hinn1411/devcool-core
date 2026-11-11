package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;

/** The message after an edit (or unchanged, for a no-op edit). */
public record EditedMessage(
    Long id,
    Long channelId,
    Long seq,
    ContentType contentType,
    String content,
    int version,
    Instant editedTime) {}
