package com.devcool.application.service.chat.strategy;

import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.port.out.MemberPort;
import org.springframework.stereotype.Component;

@Component
public class VideoMessageCreationStrategy extends AbstractMediaMessageCreationStrategy {

  public VideoMessageCreationStrategy(
      ChannelPort channelPort, MemberPort memberPort, MessagePort messagePort) {
    super(channelPort, memberPort, messagePort);
  }

  @Override
  public ContentType getSupportedType() {
    return ContentType.VIDEO;
  }
}
