package com.devcool.application.service.chat;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.SaveMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.model.Member;
import com.devcool.domain.member.port.out.MemberPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MessageService implements SaveMessageUseCase {
  private static final Logger log = LoggerFactory.getLogger(MessageService.class);
  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final MessagePort messagePort;

  @Override
  @Transactional
  public Integer save(SaveMessageCommand command) {
    // Check dup message by clientMsgId
    Channel channel = channelPort.findById(command.channelId())
        .orElseThrow(() -> {
          log.warn("Channel id {} does not exist", command.channelId());
          throw new ChannelNotFoundException(command.channelId());
        });
    // Validate member permission later
    Member member = memberPort.findMemberOfChannelByUserId(command.channelId(), command.userId())
        .orElseThrow(() -> {
          log.warn("User id {} does not exist in channel id {}", command.userId(), command.channelId());
          throw new MemberNotFoundException(command.userId());
        });

    Message message = toMessage(command, channel);
    return messagePort.save(message);
  }

  private Message toMessage(SaveMessageCommand command, Channel channel) {
    return Message.builder()
        .contentType(command.contentType())
        .content(command.content())
        .createdTime(Instant.now())
        .senderUserId(command.userId())
        .deletedTime(null)
        .editedTime(null)
        .channel(channel)
        .build();
  }
}
