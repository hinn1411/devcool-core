package com.devcool.domain.channel.port.in.command;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;

import java.time.Instant;

public record UpdateChannelCommand(
    String name,
    BoundaryType boundaryType,
    Instant expiredTime,
    ChannelType channelType
) {
}
