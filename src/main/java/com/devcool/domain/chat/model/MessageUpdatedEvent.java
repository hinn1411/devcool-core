package com.devcool.domain.chat.model;

import java.time.Instant;

/** {@code MESSAGE_UPDATED}: the state after an edit; clients keep the highest version. */
public record MessageUpdatedEvent(
    Long channelId,
    Long messageId,
    Long seq,
    Instant createdTime,
    String content,
    int version,
    Instant editedTime)
    implements RealtimeEvent {}
