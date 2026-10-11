package com.devcool.application.service.chat.strategy;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.out.MessagePort;
import org.springframework.stereotype.Component;

@Component
public class TextMessageCreationStrategy extends AbstractMessageCreationStrategy {
  public TextMessageCreationStrategy(ChannelAuthorizer channelAuthorizer, MessagePort messagePort) {
    super(channelAuthorizer, messagePort);
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.TEXT;
  }
}
