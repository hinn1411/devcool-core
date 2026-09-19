package com.devcool.application.service.chat;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService implements SaveMessageUseCase, GetMessageQuery {
  private static final Logger log = LoggerFactory.getLogger(MessageService.class);
  private final Map<ContentType, MessageCreationStrategy> creationStrategies;
  private final MessagePort messagePort;
  private final ChannelPort channelPort;
  private final MemberPort memberPort;

  public MessageService(
      List<MessageCreationStrategy> creationStrategies,
      MessagePort messagePort,
      ChannelPort channelPort,
      MemberPort memberPort) {
    this.creationStrategies = new EnumMap<>(ContentType.class);
    creationStrategies.forEach(
        strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.messagePort = messagePort;
    this.channelPort = channelPort;
    this.memberPort = memberPort;
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

  @Override
  @Transactional(readOnly = true)
  public MessageList getMessages(GetMessageCommand command) {
    if (!channelPort.existById(command.channelId())) {
      throw new ChannelNotFoundException(command.channelId());
    }
    if (memberPort.findMemberOfChannelByUserId(command.channelId(), command.userId()).isEmpty()) {
      throw new MemberNotFoundException(command.userId());
    }

    // Fetch one extra row to know whether another page exists.
    List<MessageItem> fetched =
        messagePort.findMessages(command.channelId(), command.cursorId(), command.limit() + 1);
    if (fetched.isEmpty()) {
      return new MessageList(List.of(), null, false);
    }

    boolean hasMore = fetched.size() > command.limit();
    List<MessageItem> items = hasMore ? fetched.subList(0, command.limit()) : fetched;
    return new MessageList(items, items.getLast().getId(), hasMore);
  }
}
