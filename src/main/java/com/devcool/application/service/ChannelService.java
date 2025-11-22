package com.devcool.application.service;

import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChannelService implements CreateChannelUseCase {
  private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
  private final LoadUserPort userPort;
  private final ChannelPort channelPort;

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    User creator =
        userPort
            .loadById(command.creatorId())
            .orElseThrow(() -> new UserNotFoundException(command.creatorId()));
    User leader =
        userPort
            .loadByUsername(command.leader())
            .orElseThrow(() -> new UserNotFoundException(command.leader()));
    Channel channel = buildChannel(command, creator, leader);
    return channelPort.save(channel);
  }

  private Channel buildChannel(CreateChannelCommand command, User creator, User leader) {
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
