package com.devcool.adapters.in.websocket.dto;

import java.time.Instant;

/** SEND_MESSAGE ACK data (ADR-0005 rule 3). */
public record WsMessageAck(Long messageId, Long seq, Instant createdTime) {}
