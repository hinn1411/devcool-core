package com.devcool.domain.channel.port.in.command;

import com.devcool.domain.channel.model.ChannelCursor;

/** {@code cursor} is null for the first page. */
public record GetChannelCommand(Long memberId, ChannelCursor cursor, Integer limit) {}
