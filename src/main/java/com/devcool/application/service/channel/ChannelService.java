package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelAction;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.policy.ChannelUpdatePolicy;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.GetChannelQuery;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.exception.MemberAlreadyInChannelException;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Clock;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelService implements CreateChannelUseCase, UpdateChannelUseCase, GetChannelQuery {
  private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
  private static final int LOUNGE_MAX_PEOPLE = 11;
  private final Map<ChannelType, ChannelCreationStrategy> creationStrategies;
  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final UserPort userPort;
  private final ChannelAuthorizer channelAuthorizer;
  private final Clock clock;

  public ChannelService(
      List<ChannelCreationStrategy> creationStrategies,
      ChannelPort channelPort,
      MemberPort memberPort,
      UserPort userPort,
      ChannelAuthorizer channelAuthorizer,
      Clock clock) {
    this.channelPort = channelPort;
    this.creationStrategies = new EnumMap<>(ChannelType.class);
    creationStrategies.forEach(
        strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.memberPort = memberPort;
    this.userPort = userPort;
    this.channelAuthorizer = channelAuthorizer;
    this.clock = clock;
  }

  @Override
  public Long createChannel(CreateChannelCommand command) {
    ChannelType type = command.channelType();

    ChannelCreationStrategy strategy = creationStrategies.get(type);
    if (Objects.isNull(strategy)) {
      log.warn("No channel creation strategy registered for type null");
      throw new InvalidChannelConfigException("Unsupported channel type: " + type);
    }

    return strategy.createChannel(command);
  }

  @Override
  @Transactional
  public boolean updateChannel(Long channelId, UpdateChannelCommand command) {
    ChannelAccessInfo current =
        channelAuthorizer.requireAllowed(
            channelId, command.callerId(), ChannelAction.UPDATE_CHANNEL);
    ChannelUpdatePolicy.validateUpdate(
        current.channelType(),
        command.channelType(),
        command.boundaryType(),
        command.expiredTime(),
        clock.instant());

    Channel channel =
        Channel.builder()
            .id(channelId)
            .name(command.name())
            .channelType(command.channelType())
            .boundaryType(command.boundaryType())
            .expiredTime(command.expiredTime())
            .build();
    if (!channelPort.update(channel)) {
      throw new ChannelNotFoundException(channelId);
    }
    return true;
  }

  @Override
  @Transactional
  public boolean addMember(Long channelId, AddMembersCommand command) {
    ChannelAccessInfo channel =
        channelAuthorizer.requireAllowed(channelId, command.callerId(), ChannelAction.ADD_MEMBERS);

    Set<Long> distinctMemberIds = new HashSet<>(command.userIds());
    if (distinctMemberIds.size() < command.userIds().size()) {
      throw new InvalidChannelConfigException("Member ids are duplicate");
    }

    Set<Long> existingUserIds = userPort.findExistingUserIds(distinctMemberIds);
    if (existingUserIds.size() < distinctMemberIds.size()) {
      List<Long> missingIds =
          distinctMemberIds.stream().filter(id -> !existingUserIds.contains(id)).toList();
      throw new UserNotFoundException(missingIds);
    }

    List<Member> alreadyMembers =
        memberPort.findMembersOfChannelByUserIds(channelId, existingUserIds);
    if (!alreadyMembers.isEmpty()) {
      List<Long> existedMemberIds = alreadyMembers.stream().map(Member::getId).toList();
      throw new MemberAlreadyInChannelException(existedMemberIds);
    }

    if (channel.channelType() == ChannelType.LOUNGE
        && channel.totalOfMembers() + existingUserIds.size() > LOUNGE_MAX_PEOPLE) {
      throw new InvalidChannelConfigException(
          "A lounge holds at most " + LOUNGE_MAX_PEOPLE + " people");
    }

    channelPort.increaseTotalMembers(channelId, existingUserIds.size());
    return memberPort.addMembers(channelId, existingUserIds);
  }

  @Override
  public ChannelListPage getChannels(GetChannelCommand command) {
    log.info("Get Channels by member Id");
    return channelPort.loadChannels(command.memberId(), command.cursorId(), command.limit());
  }
}
