package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import java.time.Instant;

public abstract class AbstractMessageCreationStrategy implements MessageCreationStrategy {

  /**
   * Builds the message carrying only the sender and channel ids. The sender is not loaded: the
   * service's membership check already proves the user exists, and the persistence adapter resolves
   * both foreign keys without a query.
   */
  @Override
  public Message buildMessage(CreateMessageCommand command) {
    return Message.builder()
        .senderId(command.userId())
        .content(command.content())
        .contentType(command.contentType())
        .createdTime(Instant.now())
        .channelId(command.channelId())
        .build();
  }
}
