package com.devcool.domain.chat.model;

import java.time.Instant;

/** {@code duplicate}: a retried key; the fields are the original's and nothing was written. */
public record SavedMessage(Long id, Long seq, Instant createdTime, boolean duplicate) {}
