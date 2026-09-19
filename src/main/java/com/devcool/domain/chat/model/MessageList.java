package com.devcool.domain.chat.model;

import java.util.List;

public record MessageList(List<MessageItem> items, Integer cursorId, boolean hasMore) {}
