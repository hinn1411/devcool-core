package com.devcool.domain.channel.port.in.command;

/** {@code callerId} removes {@code targetUserId} from {@code channelId}. */
public record RemoveMemberCommand(Long channelId, Long callerId, Long targetUserId) {}
