package com.devcool.domain.chat.model;

import java.time.Instant;

/**
 * What a send stored. {@code duplicate} is true when the sender retried a {@code clientMsgId} that
 * was already saved: the fields are then the original message's, and nothing new was written.
 */
public record SavedMessage(Long id, Long seq, Instant createdTime, boolean duplicate) {}
