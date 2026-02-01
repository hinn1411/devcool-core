package com.devcool.application.service.channel.strategy;

import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PrivateChatCreationStrategy extends AbstractChannelCreationStrategy
    implements ChannelCreationStrategy {
  public PrivateChatCreationStrategy(LoadUserPort userPort, ChannelPort channelPort) {
    super(userPort, channelPort);
  }

  @Override
  public ChannelType getSupportedType() {
    return ChannelType.PRIVATE_CHAT;
  }

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    validate(command);

    List<Member> members = getMembers(command.memberIds());
    User creator = loadUser(command.creatorId());
    Channel channel = buildChannel(command, creator, null, members);
    return channelPort.save(channel);
  }

  private void validate(CreateChannelCommand command) {
    ChannelType channelType = command.channelType();
    BoundaryType boundaryType = command.boundaryType();
    int totalOfMembers = command.memberIds().size();
    Integer leaderId = command.leaderId();
    Instant expiredTime = command.expiredTime();

    if (!Objects.equals(channelType, ChannelType.PRIVATE_CHAT)) {
      log.info("Channel type must be PRIVATE_CHAT");
      throw new InvalidChannelConfigException("Channel type must be PRIVATE_CHAT");
    }

    if (Objects.nonNull(leaderId)) {
      log.info("Private chat does not have leader");
      throw new InvalidChannelConfigException("Private chat does not have leader");
    }

    if (!Objects.equals(boundaryType, BoundaryType.PRIVATE)) {
      log.info("Private chat visibility is PRIVATE");
      throw new InvalidChannelConfigException("Private chat visibility must be PRIVATE");
    }

    if (totalOfMembers != 1) {
      log.info("Private chat only has 1 members");
      throw new InvalidChannelConfigException("Private chat only has 1 member");
    }

    if (Objects.nonNull(expiredTime)) {
      log.info("Private chat must not have expired time");
      throw new InvalidChannelConfigException("Private chat must not have expired time");
    }
  }
}
