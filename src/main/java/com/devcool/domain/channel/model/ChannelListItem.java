package com.devcool.domain.channel.model;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.chat.model.ReplyPreview;

/**
 * One sidebar row. {@code unreadCount = last_seq − last_read_seq} (P3-T10); {@code lastMessage}
 * follows the {@link ReplyPreview} rules and is null for a channel with no messages.
 */
public record ChannelListItem(
    Long id,
    String name,
    ChannelType channelType,
    BoundaryType boundaryType,
    long unreadCount,
    ReplyPreview lastMessage) {}
