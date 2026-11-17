package com.devcool.application.service.channel.strategy;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.OpenDirectChatUseCase;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.user.exception.UserDuplicateException;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PrivateChatCreationStrategy extends AbstractChannelCreationStrategy
    implements ChannelCreationStrategy {
  private final OpenDirectChatUseCase directChats;

  public PrivateChatCreationStrategy(
      UserPort userPort, ChannelPort channelPort, OpenDirectChatUseCase directChats) {
    super(userPort, channelPort);
    this.directChats = directChats;
  }

  @Override
  public ChannelType getSupportedType() {
    return ChannelType.PRIVATE_CHAT;
  }

  @Override
  public Long createChannel(CreateChannelCommand command) {
    validate(command);
    // Same find-or-create as POST /channels/direct, so this path can't add a second DM (P3-T12)
    return directChats
        .openDirectChat(new OpenDirectChatCommand(command.creatorId(), command.memberIds().get(0)))
        .channelId();
  }

  private void validate(CreateChannelCommand command) {
    ChannelType channelType = command.channelType();
    BoundaryType boundaryType = command.boundaryType();
    int totalOfMembers = command.memberIds().size();
    Long leaderId = command.leaderId();
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

    if (command.memberIds().contains(command.creatorId())) {
      log.info("Private chat participant must not be the creator");
      throw new UserDuplicateException(List.of(command.creatorId()));
    }
  }
}
