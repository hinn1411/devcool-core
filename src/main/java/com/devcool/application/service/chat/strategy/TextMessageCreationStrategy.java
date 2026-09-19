package com.devcool.application.service.chat.strategy;

import com.devcool.domain.auth.port.out.LoadUserPort;
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
  public TextMessageCreationStrategy(
      ChannelPort channelPort,
      MemberPort memberPort,
      MessagePort messagePort,
      LoadUserPort userPort) {
    super(channelPort, memberPort, messagePort, userPort);
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.TEXT;
  }

  @Override
  public Integer createMessage(CreateMessageCommand command) {

    if (!super.isMemberInChannel(command.channelId(), command.userId())) {
      throw new MemberNotFoundException(command.userId());
    }

    Message message = super.buildMessage(command);
    return super.messagePort.save(message);
  }
}
