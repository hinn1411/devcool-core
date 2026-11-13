package com.devcool.adapters.in.web.dto.response;

/** {@code lastMessage} is null for a channel with no messages. */
public record ChannelListItemResponse(
    Long id,
    String name,
    String channelType,
    String boundaryType,
    long unreadCount,
    ReplyPreviewResponse lastMessage) {}
