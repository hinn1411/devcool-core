package com.devcool.domain.chat.model;

import java.time.Instant;

/** What a retried send needs from the message already stored under its idempotency key. */
public record MessageKey(Long id, Long channelId, Long seq, Instant createdTime) {}
