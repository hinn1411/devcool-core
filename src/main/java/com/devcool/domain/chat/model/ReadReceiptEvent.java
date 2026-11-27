package com.devcool.domain.chat.model;

/** {@code READ_RECEIPT}. */
public record ReadReceiptEvent(Long channelId, Long userId, long seq) implements RealtimeEvent {}
