package com.devcool.domain.channel.port.in.command;

public record GetChannelCommand(Integer memberId,
                                Integer cursorId,
                                Integer limit) {
}
