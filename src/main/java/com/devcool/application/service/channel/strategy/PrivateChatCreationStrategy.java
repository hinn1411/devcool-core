package com.devcool.application.service.channel.strategy;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.OpenDirectChatUseCase;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;
import com.devcool.domain.user.exception.UserDuplicateException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Legacy {@code POST /channels} for PRIVATE_CHAT: validates the generic request shape, then uses
 * the same find-or-create as {@code POST /channels/direct}, so it can't add a second DM (P3-T12).
 */
@Component
@RequiredArgsConstructor
public class PrivateChatCreationStrategy implements ChannelCreationStrategy {
  private final OpenDirectChatUseCase directChats;

  @Override
  public ChannelType getSupportedType() {
    return ChannelType.PRIVATE_CHAT;
  }

  @Override
  public Long createChannel(CreateChannelCommand command) {
    validate(command);
    Long targetUserId = command.memberIds().get(0);
    return directChats
        .openDirectChat(new OpenDirectChatCommand(command.creatorId(), targetUserId))
        .channelId();
  }

  private static void validate(CreateChannelCommand command) {
    if (command.channelType() != ChannelType.PRIVATE_CHAT) {
      throw new InvalidChannelConfigException("Channel type must be PRIVATE_CHAT");
    }
    if (command.leaderId() != null) {
      throw new InvalidChannelConfigException("Private chat does not have leader");
    }
    if (command.boundaryType() != BoundaryType.PRIVATE) {
      throw new InvalidChannelConfigException("Private chat visibility must be PRIVATE");
    }
    if (command.memberIds().size() != 1) {
      throw new InvalidChannelConfigException("Private chat only has 1 member");
    }
    if (command.expiredTime() != null) {
      throw new InvalidChannelConfigException("Private chat must not have expired time");
    }
    // Kept as USR_405 for existing clients; POST /channels/direct answers 400 CHANNEL_400
    if (command.memberIds().contains(command.creatorId())) {
      throw new UserDuplicateException(List.of(command.creatorId()));
    }
  }
}
