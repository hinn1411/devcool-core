package com.devcool.application.service.chat.strategy;


import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TextMessageCreationStrategy extends AbstractMessageCreationStrategy
    implements MessageCreationStrategy {
  public TextMessageCreationStrategy(ChannelPort channelPort, MemberPort memberPort, MessagePort messagePort) {
    super(channelPort, memberPort, messagePort);
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.TEXT;
  }

  @Override
  public Integer createMessage(CreateMessageCommand command) {
    Channel channel = super.getChannel(command.channelId());

    if (!super.isMemberInChannel(command.channelId(), command.userId())) {
      throw new MemberNotFoundException(command.userId());
    }

    Message message = super.buildMessage(command, channel);
    return super.messagePort.save(message);
  }
}
