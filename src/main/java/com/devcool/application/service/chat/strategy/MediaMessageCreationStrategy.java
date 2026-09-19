package com.devcool.application.service.chat.strategy;

import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.media.model.Media;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MediaMessageCreationStrategy extends AbstractMessageCreationStrategy
    implements MessageCreationStrategy {

  public MediaMessageCreationStrategy(
      ChannelPort channelPort,
      MemberPort memberPort,
      MessagePort messagePort,
      LoadUserPort userPort) {
    super(channelPort, memberPort, messagePort, userPort);
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.IMAGE;
  }

  @Override
  public Integer createMessage(CreateMessageCommand command) {
    if (!super.isMemberInChannel(command.channelId(), command.userId())) {
      throw new MemberNotFoundException(command.userId());
    }

    Message message = buildMessage(command);

    return messagePort.save(message);
  }

  @Override
  public Message buildMessage(CreateMessageCommand command) {
    Message message = super.buildMessage(command);
    Media media = Media.builder().path(command.content()).createdTime(Instant.now()).build();
    message.setMedia(media);
    message.setContent(null);
    return message;
  }
}
