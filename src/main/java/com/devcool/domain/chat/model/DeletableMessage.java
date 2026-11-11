package com.devcool.domain.chat.model;

import java.time.Instant;

/** What a delete checks, read whether or not the message is already deleted. */
public record DeletableMessage(Long id, Long channelId, Long senderId, Instant deletedTime) {}
