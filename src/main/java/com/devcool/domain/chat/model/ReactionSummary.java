package com.devcool.domain.chat.model;

/** One emoji on one message: how many members used it, and whether the viewer is one of them. */
public record ReactionSummary(String emoji, long count, boolean reactedByMe) {}
