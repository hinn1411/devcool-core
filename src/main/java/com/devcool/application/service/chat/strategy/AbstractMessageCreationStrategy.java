package com.devcool.application.service.chat.strategy;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractMessageCreationStrategy implements MessageCreationStrategy {
  protected final ChannelAuthorizer channelAuthorizer;
  protected final MessagePort messagePort;

  /**
   * Validates the channel and the sender's membership ({@link ChannelAuthorizer#requireMember})
   * before delegating the message construction to {@link #buildMessage(CreateMessageCommand)}.
   */
  @Override
  public final Long createMessage(CreateMessageCommand command) {
    channelAuthorizer.requireMember(command.channelId(), command.userId());

    return messagePort.save(buildMessage(command));
  }

  /**
   * Builds the message carrying only the sender and channel ids. The sender is not loaded: the
   * membership check above already proves the user exists, and the persistence adapter resolves
   * both foreign keys without a query.
   */
  protected Message buildMessage(CreateMessageCommand command) {
    return Message.builder()
        .senderId(command.userId())
        .content(command.content())
        .contentType(command.contentType())
        .createdTime(Instant.now())
        .channelId(command.channelId())
        .build();
  }
}
