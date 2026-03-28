package com.devcool.application.service.chat;

import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.port.out.MemberPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MessageService implements SaveMessageUseCase {
  private static final Logger log = LoggerFactory.getLogger(MessageService.class);
  private final Map<ContentType, MessageCreationStrategy> creationStrategies;
  private final ChannelPort channelPort;
  private final MemberPort memberPort;
  private final MessagePort messagePort;

  public MessageService(List<MessageCreationStrategy> creationStrategies,
                        ChannelPort channelPort,
                        MemberPort memberPort,
                        MessagePort messagePort) {
    this.creationStrategies = new EnumMap<>(ContentType.class);
    creationStrategies.forEach(strategy
        -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.channelPort = channelPort;
    this.memberPort = memberPort;
    this.messagePort = messagePort;
  }

  @Override
  @Transactional
  public Integer save(CreateMessageCommand command) {
    MessageCreationStrategy strategy = creationStrategies.get(command.contentType());
    if (Objects.isNull(strategy)) {
      log.warn("No message creation strategy registered for type null");
      throw new InvalidMessageConfigException("Unsupported channel type: " + command.contentType());
    }

    return strategy.createMessage(command);
  }

  private Message toMessage(CreateMessageCommand command, Channel channel) {
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
