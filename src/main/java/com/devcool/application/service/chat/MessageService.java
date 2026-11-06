package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class MessageService implements SaveMessageUseCase, GetMessageQuery {
  private static final Logger log = LoggerFactory.getLogger(MessageService.class);
  private final Map<ContentType, MessageCreationStrategy> creationStrategies;
  private final MessagePort messagePort;
  private final ChannelPort channelPort;
  private final ChannelAuthorizer channelAuthorizer;
  private final TransactionOperations transactions;

  public MessageService(
      List<MessageCreationStrategy> creationStrategies,
      MessagePort messagePort,
      ChannelPort channelPort,
      ChannelAuthorizer channelAuthorizer,
      TransactionOperations transactions) {
    this.creationStrategies = new EnumMap<>(ContentType.class);
    creationStrategies.forEach(
        strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.messagePort = messagePort;
    this.channelPort = channelPort;
    this.channelAuthorizer = channelAuthorizer;
    this.transactions = transactions;
  }

  @Override
  public SavedMessage save(CreateMessageCommand command) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Transactional(readOnly = true)
  public MessageList getMessages(GetMessageCommand command) {
    channelAuthorizer.requireMember(command.channelId(), command.userId());

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
