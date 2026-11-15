package com.devcool.domain.channel.model;

import java.time.Instant;

/**
 * Keyset position in the recency-ordered channel list (P3-T10): the last row's {@code
 * last_message_at} (null for a channel with no messages) and its id, the tie-breaker.
 */
public record ChannelCursor(Instant lastMessageAt, long id) {}
