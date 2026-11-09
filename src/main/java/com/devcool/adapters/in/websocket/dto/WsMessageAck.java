package com.devcool.adapters.in.websocket.dto;

import java.time.Instant;

/** ACK data for SEND_MESSAGE: the saved message's ids (ADR-0005 rule 3). */
public record WsMessageAck(Long messageId, Long seq, Instant createdTime) {}
