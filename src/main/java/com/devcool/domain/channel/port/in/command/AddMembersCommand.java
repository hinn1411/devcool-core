package com.devcool.domain.channel.port.in.command;

import java.util.List;

public record AddMembersCommand (
    List<Integer> userIds) { }
