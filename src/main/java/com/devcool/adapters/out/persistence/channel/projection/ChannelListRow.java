package com.devcool.adapters.out.persistence.channel.projection;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;

public record ChannelListRow(
    Integer id,
    String name,
    ChannelType channelType,
    BoundaryType boundaryType
) {
}
