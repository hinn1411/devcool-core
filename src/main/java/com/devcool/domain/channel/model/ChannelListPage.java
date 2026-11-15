package com.devcool.domain.channel.model;

import java.util.List;

/** {@code nextCursor} is null when the page is empty. */
public record ChannelListPage(
    List<ChannelListItem> items, ChannelCursor nextCursor, boolean hasMore) {}
