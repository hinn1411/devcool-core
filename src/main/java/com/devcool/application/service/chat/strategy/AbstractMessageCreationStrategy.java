package com.devcool.application.service.chat.strategy;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.port.out.MemberPort;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@RequiredArgsConstructor
abstract public class AbstractMessageCreationStrategy {
  protected final ChannelPort channelPort;
  protected final MemberPort memberPort;
  protected final MessagePort messagePort;


  protected Channel getChannel(Integer channelId) {
    return channelPort.findById(channelId).orElseThrow(() -> new ChannelNotFoundException(channelId));
  }

  protected boolean isMemberInChannel(Integer channelId, Integer userId) {
    return memberPort.findMemberOfChannelByUserId(channelId, userId).map(Objects::nonNull).isPresent();
  }

  protected Message buildMessage(CreateMessageCommand command, Channel channel) {
    return Message.builder()
        .senderUserId(command.userId())
        .content(command.content())
        .contentType(command.contentType())
        .createdTime(Instant.now())
        .channel(channel)
        .build();
  }
}
