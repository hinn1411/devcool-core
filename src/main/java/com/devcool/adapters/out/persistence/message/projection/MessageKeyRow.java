package com.devcool.adapters.out.persistence.message.projection;

import java.time.Instant;

/** What an idempotent retry needs from the original message. */
public record MessageKeyRow(Long id, Long channelId, Long seq, Instant createdTime) {}
