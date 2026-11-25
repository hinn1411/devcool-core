package com.devcool.domain.chat.port.in.command;

/** {@code beforeSeq} null = newest page. */
public record SearchChannelMessagesCommand(
    Long userId, Long channelId, String query, Long beforeSeq, Integer limit) {}
