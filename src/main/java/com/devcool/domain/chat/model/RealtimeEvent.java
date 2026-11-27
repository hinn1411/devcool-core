package com.devcool.domain.chat.model;

/** A push to every live subscriber of {@link #channelId()}. */
public sealed interface RealtimeEvent
    permits MessageEvent,
        MessageUpdatedEvent,
        MessageDeletedEvent,
        ReactionEvent,
        ReadReceiptEvent {
  Long channelId();
}
