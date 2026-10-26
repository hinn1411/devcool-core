package com.devcool.domain.channel.port.in.command;

import java.util.List;

public record AddMembersCommand(Long callerId, List<Long> userIds) {}
