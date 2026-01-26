package com.devcool.adapters.in.websocket.dto;

import com.devcool.domain.model.enums.ContentType;

public record WsClientFrame (
    WsMessageType action,
    Integer channelId,
    ContentType contentType,
    String content,
    String clientMsgId // Idempotent key
) { }
