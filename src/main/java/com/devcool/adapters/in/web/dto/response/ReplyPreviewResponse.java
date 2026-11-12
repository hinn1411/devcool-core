package com.devcool.adapters.in.web.dto.response;

/** The parent of a reply; {@code preview} is null for media or a deleted parent. */
public record ReplyPreviewResponse(
    Long id,
    Long seq,
    Long senderId,
    String senderName,
    String contentType,
    String preview,
    boolean deleted) {}
