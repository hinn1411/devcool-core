package com.devcool.application.service.chat.strategy;

import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.media.model.Media;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;

/**
 * Base for media messages: the command's {@code content} is the S3 object key, so it is moved to
 * {@link Media#getPath()} and the message's own content is cleared.
 */
public abstract class AbstractMediaMessageCreationStrategy extends AbstractMessageCreationStrategy {

  protected AbstractMediaMessageCreationStrategy(
      ChannelPort channelPort, MemberPort memberPort, MessagePort messagePort) {
    super(channelPort, memberPort, messagePort);
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
