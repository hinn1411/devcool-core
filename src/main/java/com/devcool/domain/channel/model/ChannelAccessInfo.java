package com.devcool.domain.channel.model;

import com.devcool.domain.channel.model.enums.ChannelType;

/**
 * The facts an access decision needs about a channel, without loading the whole aggregate. {@code
 * leaderId} is the FORUM leader; null for other types.
 */
public record ChannelAccessInfo(ChannelType channelType, int totalOfMembers, Long leaderId) {}
