package com.devcool.domain.channel.model;

import java.time.Instant;

/** Channel-list keyset position; {@code lastMessageAt} is null for an empty channel. */
public record ChannelCursor(Instant lastMessageAt, long id) {}
