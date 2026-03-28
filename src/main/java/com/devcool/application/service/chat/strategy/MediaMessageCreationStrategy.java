package com.devcool.application.service.chat.strategy;


import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.media.model.Media;
import com.devcool.domain.media.port.out.MediaPort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class MediaMessageCreationStrategy extends AbstractMessageCreationStrategy
    implements MessageCreationStrategy {
  private final MediaPort mediaPort;

  public MediaMessageCreationStrategy(ChannelPort channelPort, MemberPort memberPort, MessagePort messagePort, MediaPort mediaPort) {
    super(channelPort, memberPort, messagePort);
    this.mediaPort = mediaPort;
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.IMAGE;
  }

  @Override
  public Integer createMessage(CreateMessageCommand command) {
    Channel channel = super.getChannel(command.channelId());

    if (!super.isMemberInChannel(command.channelId(), command.userId())) {
      throw new MemberNotFoundException(command.userId());
    }

    Message message = buildMessage(command, channel);
    Integer messageId = messagePort.save(message);
    message.setId(messageId);
    Media media = toMedia(command, message);
    mediaPort.save(media);
    return messageId;
  }

  private Media toMedia(CreateMessageCommand command, Message message) {
    return Media.builder()
        .path(command.content())
        .createdTime(Instant.now())
        .message(message)
        .build();
  }
}
