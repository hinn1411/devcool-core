package com.devcool.domain.channel.port.in.command;

public record RemoveMemberCommand(Long channelId, Long callerId, Long targetUserId) {}
