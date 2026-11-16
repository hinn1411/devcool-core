package com.devcool.domain.channel.port.in.command;

/** {@code userId} leaves {@code channelId}. */
public record LeaveChannelCommand(Long channelId, Long userId) {}
