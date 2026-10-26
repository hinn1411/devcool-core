package com.devcool.domain.channel.port.in.command;

public record GetChannelCommand(Long memberId, Long cursorId, Integer limit) {}
