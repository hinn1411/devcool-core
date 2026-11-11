package com.devcool.adapters.in.web.dto.response;

import java.time.Instant;

public record EditMessageResponse(
    Long id,
    Long channelId,
    Long seq,
    String content,
    String contentType,
    Integer version,
    Instant editedTime) {}
