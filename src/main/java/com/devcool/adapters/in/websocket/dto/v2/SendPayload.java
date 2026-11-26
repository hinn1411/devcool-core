package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.enums.ContentType;

public record SendPayload(
    String clientMsgId, ContentType contentType, String content, Long replyToId) {}
