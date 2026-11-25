package com.devcool.domain.channel.port.in.command;

public record OpenDirectChatCommand(Long callerId, Long targetUserId) {}
