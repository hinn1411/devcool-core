package com.devcool.domain.chat.model;

/** {@code READ_RECEIPT}: {@code userId} has seen the channel up to {@code seq}. */
public record ReadReceiptEvent(Long channelId, Long userId, long seq) implements RealtimeEvent {}
