package com.devcool.application.service.channel.strategy;

import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.channel.exception.InvalidChannelConfigException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.policy.ChannelCreationStrategy;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.user.exception.UserDuplicateException;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoungeCreationStrategy extends AbstractChannelCreationStrategy
    implements ChannelCreationStrategy {

  public LoungeCreationStrategy(LoadUserPort userPort, ChannelPort channelPort) {
    super(userPort, channelPort);
  }

  @Override
  public ChannelType getSupportedType() {
    return ChannelType.LOUNGE;
  }

  @Override
  public Integer createChannel(CreateChannelCommand command) {
    validate(command);

    List<Member> members = getMembers(command.memberIds());
    Set<Integer> distinctMemberIds = new HashSet<>(command.memberIds());
    if (distinctMemberIds.size() < command.memberIds().size()) {
      throw new UserDuplicateException(command.memberIds());
    }
    if (members.size() < distinctMemberIds.size()) {
      Set<Integer> foundIds =
          members.stream().map(member -> member.getUser().getId()).collect(Collectors.toSet());
      List<Integer> missingIds =
          distinctMemberIds.stream().filter(id -> !foundIds.contains(id)).toList();
      throw new UserNotFoundException(missingIds);
    }
    User creator = loadUser(command.creatorId());
    Channel channel = buildChannel(command, creator, null, members);
    return channelPort.save(channel);
  }

  private void validate(CreateChannelCommand command) {
    ChannelType channelType = command.channelType();
    int totalOfMembers = command.memberIds().size();
    Integer leaderId = command.leaderId();

    if (!Objects.equals(channelType, ChannelType.LOUNGE)) {
      log.info("Channel type must be LOUNGE");
      throw new InvalidChannelConfigException("Channel type must be LOUNGE");
    }

    if (!(1 <= totalOfMembers && totalOfMembers <= 10)) {
      log.info("Total members allowed are from 1 to 10");
      throw new InvalidChannelConfigException("Total members allowed are from 1 to 10");
    }

    if (!Objects.isNull(leaderId)) {
      log.info("Leader is not allowed in lounge");
      throw new InvalidChannelConfigException("Leader is not allowed in lounge");
    }
  }
}
