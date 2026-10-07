package com.devcool.application.service.channel.strategy;

import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.user.exception.UserDuplicateException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ForumCreationStrategy extends AbstractChannelCreationStrategy
    implements ChannelCreationStrategy {

  public ForumCreationStrategy(UserPort userPort, ChannelPort channelPort) {
    super(userPort, channelPort);
  }

  @Override
  public ChannelType getSupportedType() {
    return ChannelType.FORUM;
  }

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    validate(command);
    rejectPersonListedTwice(command);

    List<Member> members = new ArrayList<>(getMembers(command.memberIds()));
    User creator = loadUser(command.creatorId());

    // A user can only post in a channel they are a Member of, so the creator and the leader need
    // rows too. Each person gets exactly one row: a creator who is also the leader stays the
    // single CREATOR instead of also being added as LEADER.
    Instant joinedTime = Instant.now();
    members.add(toMember(creator, MemberType.CREATOR, joinedTime));

    boolean creatorIsLeader = Objects.equals(command.creatorId(), command.leaderId());
    User leader = creatorIsLeader ? creator : loadUser(command.leaderId());
    if (!creatorIsLeader) {
      members.add(toMember(leader, MemberType.LEADER, joinedTime));
    }

    Channel channel = buildChannel(command, creator, leader, members);
    return channelPort.save(channel);
  }

  /**
   * Every person gets exactly one member row, so a person cannot be listed twice: neither repeated
   * in {@code memberIds}, nor as the creator or the leader on top of being in {@code memberIds}
   * (they already get their own row).
   */
  private void rejectPersonListedTwice(CreateChannelCommand command) {
    Set<Integer> distinctMemberIds = new HashSet<>(command.memberIds());

    if (distinctMemberIds.size() < command.memberIds().size()) {
      log.info("Member ids are duplicate");
      throw new UserDuplicateException(command.memberIds());
    }

    if (distinctMemberIds.contains(command.creatorId())) {
      log.info("Creator must not be listed in member ids");
      throw new UserDuplicateException(List.of(command.creatorId()));
    }

    if (distinctMemberIds.contains(command.leaderId())) {
      log.info("Leader must not be listed in member ids");
      throw new UserDuplicateException(List.of(command.leaderId()));
    }
  }

  private void validate(CreateChannelCommand command) {
    ChannelType channelType = command.channelType();
    int totalOfMembers = command.memberIds().size();
    Integer leaderId = command.leaderId();
    Instant expiredTime = command.expiredTime();

    if (!Objects.equals(channelType, ChannelType.FORUM)) {
      log.info("Channel type must be FORUM");
      throw new InvalidChannelConfigException("Channel type must be FORUM");
    }

    if (!(1 <= totalOfMembers && totalOfMembers <= 10)) {
      log.info("Total members allowed are from 1 to 10");
      throw new InvalidChannelConfigException("Total members allowed are from 1 to 10");
    }

    if (Objects.isNull(leaderId)) {
      log.info("In forum, leader must be present");
      throw new InvalidChannelConfigException("In forum, leader must be present");
    }

    if (Objects.nonNull(expiredTime)) {
      log.info("Forum must not have expired time");
      throw new InvalidChannelConfigException("Forum must not have expired time");
    }
  }
}
