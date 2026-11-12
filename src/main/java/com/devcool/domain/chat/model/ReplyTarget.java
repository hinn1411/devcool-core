package com.devcool.domain.chat.model;

import java.time.Instant;

/** What a reply needs to know about its parent; {@code deletedTime} set if it was deleted. */
public record ReplyTarget(Long channelId, Instant deletedTime) {}
