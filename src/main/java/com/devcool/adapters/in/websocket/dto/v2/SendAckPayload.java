package com.devcool.adapters.in.websocket.dto.v2;

import java.time.Instant;

public record SendAckPayload(String clientMsgId, Long messageId, Long seq, Instant createdTime) {}
