package com.devcool.domain.channel.port.in.command;

public record TransferLeaderCommand(Long channelId, Long callerId, Long newLeaderId) {}
