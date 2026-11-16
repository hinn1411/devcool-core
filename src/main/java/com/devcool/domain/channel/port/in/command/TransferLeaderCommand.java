package com.devcool.domain.channel.port.in.command;

/**
 * {@code callerId}, the current leader of {@code channelId}, hands the role to {@code newLeaderId}.
 */
public record TransferLeaderCommand(Long channelId, Long callerId, Long newLeaderId) {}
