package com.devcool.application.service.chat.strategy;

import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.port.out.MemberPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractMessageCreationStrategy {
  protected final ChannelPort channelPort;
  protected final MemberPort memberPort;
  protected final MessagePort messagePort;
  protected final LoadUserPort userPort;

  protected User getUser(Integer userId) {
    return userPort.loadById(userId).orElseThrow(() -> new UserNotFoundException(userId));
  }

  protected Channel getChannel(Integer channelId) {
    return channelPort
        .findById(channelId)
        .orElseThrow(() -> new ChannelNotFoundException(channelId));
  }

  protected boolean isMemberInChannel(Integer channelId, Integer userId) {
    return memberPort
        .findMemberOfChannelByUserId(channelId, userId)
        .map(Objects::nonNull)
        .isPresent();
  }

  protected Message buildMessage(CreateMessageCommand command) {
    Channel channel = getChannel(command.channelId());
    User user = getUser(command.userId());

    return Message.builder()
        .user(user)
        .content(command.content())
        .contentType(command.contentType())
        .createdTime(Instant.now())
        .channel(channel)
        .build();
  }
}
