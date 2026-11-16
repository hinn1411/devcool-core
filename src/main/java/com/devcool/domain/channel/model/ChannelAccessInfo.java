package com.devcool.domain.channel.model;

import com.devcool.domain.channel.model.enums.ChannelType;

/** Facts for an access decision. {@code leaderId}: FORUM only, else null. */
public record ChannelAccessInfo(ChannelType channelType, int totalOfMembers, Long leaderId) {}
