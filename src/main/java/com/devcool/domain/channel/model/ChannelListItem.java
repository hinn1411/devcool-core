package com.devcool.domain.channel.model;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;

public record ChannelListItem(
    Integer id,
    String name,
    ChannelType channelType,
    BoundaryType boundaryType
) {
}
