package com.devcool.adapters.in.web.dto.response;

import java.time.Instant;
import java.util.List;

public record MessageItemResponse(
    Long id,
    Long seq,
    String content,
    String contentType,
    Instant createdTime,
    Integer version,
    Instant editedTime,
    Instant deletedTime,
    Long userId,
    String senderName,
    String senderAvatar,
    ReplyPreviewResponse replyTo,
    List<ReactionSummaryResponse> reactions) {}
