package com.devcool.application.service.channel;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.ChannelAccessInfo;
import com.devcool.domain.channel.model.ChannelListPage;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelAction;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.policy.ChannelPermissionPolicy;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.GetChannelQuery;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.channel.port.in.command.AddMembersCommand;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.in.command.GetChannelCommand;
import com.devcool.domain.channel.port.in.command.UpdateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.exception.MemberAlreadyInChannelException;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.port.out.UserPort;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChannelService implements CreateChannelUseCase, UpdateChannelUseCase, GetChannelQuery {
  private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
  // Creator plus ten members (docs/plans/architecture/03-chat-system-design.md).
  private static final int LOUNGE_MAX_PEOPLE = 11;
  private final Map<ChannelType, ChannelCreationStrategy> creationStrategies;
  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final UserPort userPort;

  public ChannelService(
      List<ChannelCreationStrategy> creationStrategies,
      ChannelPort channelPort,
      MemberPort memberPort,
      UserPort userPort) {
    this.channelPort = channelPort;
    this.creationStrategies = new EnumMap<>(ChannelType.class);
    creationStrategies.forEach(
        strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
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
  @Transactional
  public boolean updateChannel(Integer channelId, UpdateChannelCommand command) {
    requireAllowed(channelId, command.callerId(), ChannelAction.UPDATE_CHANNEL);

    Channel channel =
        Channel.builder()
            .id(channelId)
            .name(command.name())
            .channelType(command.channelType())
            .expiredTime(command.expiredTime())
            .build();
    if (!channelPort.update(channel)) {
      throw new ChannelNotFoundException(channelId);
    }
    return true;
  }

  @Override
  @Transactional
  public boolean addMember(Integer channelId, AddMembersCommand command) {
    ChannelAccessInfo channel =
        requireAllowed(channelId, command.callerId(), ChannelAction.ADD_MEMBERS);

    Set<Integer> distinctMemberIds = new HashSet<>(command.userIds());
    if (distinctMemberIds.size() < command.userIds().size()) {
      throw new InvalidChannelConfigException("Member ids are duplicate");
    }

    Set<Integer> existingUserIds = userPort.findExistingUserIds(distinctMemberIds);
    if (existingUserIds.size() < distinctMemberIds.size()) {
      List<Integer> missingIds =
          distinctMemberIds.stream().filter(id -> !existingUserIds.contains(id)).toList();
      throw new UserNotFoundException(missingIds);
    }

    List<Member> alreadyMembers =
        memberPort.findMembersOfChannelByUserIds(channelId, existingUserIds);
    if (!alreadyMembers.isEmpty()) {
      List<Integer> existedMemberIds = alreadyMembers.stream().map(Member::getId).toList();
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

  /**
   * Channel exists → caller is a member → the action exists for this channel type → the caller's
   * role may perform it. Returns the channel's access info for the checks that follow.
   */
  private ChannelAccessInfo requireAllowed(
      Integer channelId, Integer callerId, ChannelAction action) {

    ChannelAccessInfo channelInfo =
        channelPort
            .findAccessInfo(channelId)
            .orElseThrow(() -> new ChannelNotFoundException(channelId));
    ChannelType type = channelInfo.channelType();
    MemberType callerRole =
        memberPort
            .findRoleOfMember(channelId, callerId)
            .orElseThrow(() -> new MemberNotFoundException(callerId));
    if (!ChannelPermissionPolicy.supports(type, action)) {
      log.warn("Action {} not allowed in channel type {}", action, channelInfo.channelType());
      throw new InvalidChannelConfigException("Action not supported");
    }

    if (!ChannelPermissionPolicy.allows(type, callerRole, action)) {
      log.warn(
          "Role {} cannot perform action {} in {}", callerRole, action, channelInfo.channelType());
      throw new ForbiddenException("Action not allowed");
    }

    return channelInfo;
  }

  @Override
  public ChannelListPage getChannels(GetChannelCommand command) {
    log.info("Get Channels by member Id");
    return channelPort.loadChannels(command.memberId(), command.cursorId(), command.limit());
  }
}
