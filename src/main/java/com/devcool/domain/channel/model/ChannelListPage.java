package com.devcool.domain.channel.model;

import java.util.List;

public record ChannelListPage(List<ChannelListItem> items, Long cursorId, boolean hasMore) {}
