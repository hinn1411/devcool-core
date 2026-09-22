package com.devcool.application.service.chat.strategy;

import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.media.model.Media;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MediaMessageCreationStrategy extends AbstractMessageCreationStrategy {

  public MediaMessageCreationStrategy(
      ChannelPort channelPort, MemberPort memberPort, MessagePort messagePort) {
    super(channelPort, memberPort, messagePort);
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.IMAGE;
  }

  @Override
  protected Message buildMessage(CreateMessageCommand command) {
    Message message = super.buildMessage(command);
    Media media = Media.builder().path(command.content()).createdTime(Instant.now()).build();
    message.setMedia(media);
    message.setContent(null);
    return message;
  }
}
