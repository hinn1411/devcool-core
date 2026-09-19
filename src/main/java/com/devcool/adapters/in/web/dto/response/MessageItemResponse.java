package com.devcool.adapters.in.web.dto.response;

import java.time.Instant;

public record MessageItemResponse(
    Integer id,
    String content,
    String contentType,
    Instant createdTime,
    Integer userId,
    String senderName,
    String senderAvatar) {}
