package com.devcool.domain.chat.model;

/** {@code REACTION}; {@code on} false = removed. */
public record ReactionEvent(Long channelId, Long messageId, String emoji, Long userId, boolean on)
    implements RealtimeEvent {}
