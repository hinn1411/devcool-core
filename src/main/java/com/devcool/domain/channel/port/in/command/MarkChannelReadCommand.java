package com.devcool.domain.channel.port.in.command;

/** "I have seen everything up to {@code seq}" from {@code userId} in {@code channelId}. */
public record MarkChannelReadCommand(Long channelId, Long userId, long seq) {}
