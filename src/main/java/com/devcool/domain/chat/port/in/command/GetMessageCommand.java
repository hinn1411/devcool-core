package com.devcool.domain.chat.port.in.command;

/** At most one of {@code cursorId} (deprecated), {@code beforeSeq}, {@code afterSeq} is set. */
public record GetMessageCommand(
    Long userId, Long channelId, Long cursorId, Long beforeSeq, Long afterSeq, Integer limit) {}
