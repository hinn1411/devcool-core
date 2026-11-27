package com.devcool.domain.chat.model;

import java.time.Instant;

/** A message's channel and deletion time, for replies and reactions. */
public record MessageRef(Long channelId, Instant deletedTime) {}
