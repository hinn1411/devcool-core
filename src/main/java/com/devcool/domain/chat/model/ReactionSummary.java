package com.devcool.domain.chat.model;

/** One emoji on one message: its count, and whether the viewer used it. */
public record ReactionSummary(String emoji, long count, boolean reactedByMe) {}
