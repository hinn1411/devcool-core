package com.devcool.domain.channel.model;

import com.devcool.domain.channel.model.enums.ChannelType;

/** The facts an access decision needs about a channel, without loading the whole aggregate. */
public record ChannelAccessInfo(ChannelType channelType, int totalOfMembers) {}
