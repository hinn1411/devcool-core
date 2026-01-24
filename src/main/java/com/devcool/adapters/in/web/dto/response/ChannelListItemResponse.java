package com.devcool.adapters.in.web.dto.response;

public record ChannelListItemResponse (
    Integer id,
    String name,
    String channelType,
    String boundaryType
) {
}
