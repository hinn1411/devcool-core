package com.devcool.adapters.in.web.dto.response;

import java.time.Instant;

public record MessageItemResponse(
    Long id,
    Long seq,
    String content,
    String contentType,
    Instant createdTime,
    Long userId,
    String senderName,
    String senderAvatar) {}
