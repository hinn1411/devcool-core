package com.devcool.application.service.channel.strategy;

import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.in.command.CreateChannelCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.model.enums.MemberType;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractChannelCreationStrategy {
  protected final LoadUserPort userPort;
  protected final ChannelPort channelPort;

  protected User loadUser(Integer id) {
    return userPort.loadById(id).orElseThrow(() -> new UserNotFoundException(id));
  }

  protected List<User> loadUsers(List<Integer> memberIds) {
    return userPort.loadByIds(memberIds);
  }

  protected List<Member> getMembers(List<Integer> ids) {
    List<User> users = loadUsers(ids);

    Instant joinedTime = Instant.now();
    MemberType role = MemberType.MEMBER;
    return users.stream()
        .map(user -> Member.builder().joinedTime(joinedTime).role(role).user(user).build())
        .toList();
  }

  protected Member toMember(User user, MemberType memberType, Instant joinedTime) {
    return Member.builder().user(user).role(memberType).joinedTime(joinedTime).build();
  }

  protected List<Member> toMembers(List<User> users, User creator) {
    Instant joinedTime = Instant.now();
    List<Member> members = users.stream()
        .map(user -> toMember(user, MemberType.MEMBER, joinedTime))
        .collect(Collectors.toCollection(ArrayList::new));
    members.add(toMember(creator, MemberType.CREATOR, joinedTime));
    return members;
  }


  protected Channel buildChannel(
      CreateChannelCommand command, User creator, User leader, List<Member> members) {
    return Channel.builder()
        .name(command.name())
        .boundaryType(command.boundaryType())
        .expiredTime(command.expiredTime())
        .channelType(command.channelType())
        .totalOfMembers(1 + (Objects.isNull(leader) ? 0 : 1) + command.memberIds().size())
        .creator(creator)
        .leader(leader)
        .members(members)
        .build();
  }
}
