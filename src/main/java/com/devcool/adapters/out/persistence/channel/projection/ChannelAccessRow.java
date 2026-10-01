package com.devcool.adapters.out.persistence.channel.projection;

import com.devcool.domain.channel.model.enums.ChannelType;

public record ChannelAccessRow(ChannelType channelType, Integer totalOfMembers) {}
