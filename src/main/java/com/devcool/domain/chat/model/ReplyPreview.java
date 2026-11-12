package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;

/**
 * The parent shown above a reply: {@code preview} is a text snippet, null for media or a deleted
 * parent.
 */
public record ReplyPreview(
    Long id,
    Long seq,
    Long senderId,
    String senderName,
    ContentType contentType,
    String preview,
    boolean deleted) {}
