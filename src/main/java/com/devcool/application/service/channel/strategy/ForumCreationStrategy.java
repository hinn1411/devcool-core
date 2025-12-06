package com.devcool.application.service.channel.strategy;

import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Component
public class ForumCreationStrategy extends AbstractChannelCreationStrategy implements ChannelCreationStrategy {

  public ForumCreationStrategy(LoadUserPort userPort, ChannelPort channelPort) {
    super(userPort, channelPort);
  }
  @Override
  public ChannelType getSupportedType() {
    return ChannelType.FORUM;
  }

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    validate(command);

    User creator = loadUser(command.creatorId());
    User leader = loadUser(command.leaderId());

    Channel channel = buildChannel(command, creator, leader);
    return channelPort.save(channel);
  }

  private void validate(CreateChannelCommand command) {
    int totalOfMembers = command.memberIds().size();
    Integer leaderId = command.leaderId();
    Instant expiredTime = command.expiredTime();

    if (!(1 <= totalOfMembers && totalOfMembers <= 10)) {
      log.info("Total members allowed are from 1 to 10");
      throw new InvalidChannelConfigException("Total members allowed are from 1 to 10");
    }

    if (Objects.isNull(leaderId)) {
      log.info("In forum, leader must be present");
      throw new InvalidChannelConfigException("In forum, leader must be present");
    }

    if (Objects.isNull(expiredTime)) {
      log.info("Forum must not have expired time");
      throw new InvalidChannelConfigException("Forum must not have expired time");
    }
  }
}
