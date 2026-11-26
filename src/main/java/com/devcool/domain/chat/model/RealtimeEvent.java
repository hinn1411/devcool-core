package com.devcool.domain.chat.model;

/** Something every live subscriber of {@link #channelId()} should see (ADR-0005 event types). */
public sealed interface RealtimeEvent
    permits MessageEvent,
        MessageUpdatedEvent,
        MessageDeletedEvent,
        ReactionEvent,
        ReadReceiptEvent {
  Long channelId();
}
