package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.exception.MemberAlreadyInChannelException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.port.out.UserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChannelService implements CreateChannelUseCase, UpdateChannelUseCase {
  private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
  private final Map<ChannelType, ChannelCreationStrategy> creationStrategies;
  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final UserPort userPort;

  public ChannelService(List<ChannelCreationStrategy> creationStrategies, ChannelPort channelPort, MemberPort memberPort, UserPort userPort) {
    this.channelPort = channelPort;
    this.creationStrategies = new EnumMap<>(ChannelType.class);
    creationStrategies.forEach(strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.memberPort = memberPort;
    this.userPort = userPort;
  }

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    ChannelType type = command.channelType();

    ChannelCreationStrategy strategy = creationStrategies.get(type);
    if (Objects.isNull(strategy)) {
      log.warn("No channel creation strategy registered for type null");
      throw new InvalidChannelConfigException("Unsupported channel type: " + type);
    }

    return strategy.createChannel(command);
  }


  @Override
  public boolean updateChannel(Integer channelId, UpdateChannelCommand command) {
    Channel existedChannel = channelPort.findById(channelId)
        .orElseThrow(() -> new ChannelNotFoundException("Channel not found"));
    Channel channel = updateChannel(existedChannel, command);
    return channelPort.update(channel);
  }

  @Override
  public boolean addMember(Integer channelId, AddMembersCommand command) {
    Set<Integer> distinctMemberIds = new HashSet<>(command.userIds());
    if (distinctMemberIds.size() < command.userIds().size()) {
      throw new InvalidChannelConfigException("Member ids are duplicate");
    }

    Set<Integer> existingUserIds = userPort.findExistingUserIds(distinctMemberIds);
    if (existingUserIds.size() < distinctMemberIds.size()) {
      List<Integer> missingIds = distinctMemberIds.stream().filter(id -> !existingUserIds.contains(id)).toList();
      throw new UserNotFoundException(missingIds);
    }

    boolean isChannelExisted = channelPort.existById(channelId);
    if (!isChannelExisted) {
      throw new ChannelNotFoundException("Channel is not found");
    }

    List<Member> alreadyMembers = memberPort.findMembersOfChannelByUserIds(channelId, existingUserIds);
    if (!alreadyMembers.isEmpty()) {
      List<Integer> existedMemberIds = alreadyMembers.stream().map(Member::getId).toList();
      throw new MemberAlreadyInChannelException(existedMemberIds);
    }

    channelPort.increaseTotalMembers(channelId, command.userIds().size());
    return memberPort.addMembers(channelId, existingUserIds);
  }

  private Channel updateChannel(Channel channel, UpdateChannelCommand command) {
    channel.setName(command.name());
    channel.setChannelType(command.channelType());
    channel.setExpiredTime(command.expiredTime());
    channel.setChannelType(command.channelType());
    return channel;
  }
}
