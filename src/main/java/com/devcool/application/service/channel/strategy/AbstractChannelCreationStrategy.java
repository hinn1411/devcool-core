package com.devcool.application.service.channel.strategy;

import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractChannelCreationStrategy {
  protected final LoadUserPort userPort;
  protected final ChannelPort channelPort;

  protected User loadUser(Integer id) {
    return userPort
        .loadById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
  }

  protected Channel buildChannel(CreateChannelCommand command, User creator, User leader) {
    return Channel.builder()
        .name(command.name())
        .boundaryType(command.boundaryType())
        .expiredTime(command.expiredTime())
        .channelType(command.channelType())
        .totalOfMembers(1)
        .creator(creator)
        .leader(leader)
        .build();
  }
}
