package com.devcool.domain.chat.port.in.command;

/** Only {@code channelId} comes from the frame; {@code connectionId} is the server's session id. */
public record UnsubscribeCommand(String connectionId, Long channelId) {}
