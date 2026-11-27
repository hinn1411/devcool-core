package com.devcool.domain.chat.model;

import java.time.Instant;

/** {@code MESSAGE_DELETED}; the seq stays as a tombstone. */
public record MessageDeletedEvent(Long channelId, Long messageId, Long seq, Instant createdTime)
    implements RealtimeEvent {}
