package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public abstract class AbstractMessageCreationStrategy implements MessageCreationStrategy {

  /** {@code createdTime} is cut to micros ({@code timestamp(6)}) so a retry's re-read matches. */
  @Override
  public Message buildMessage(CreateMessageCommand command) {
    return Message.builder()
        .senderId(command.userId())
        .content(command.content())
        .contentType(command.contentType())
        .createdTime(Instant.now().truncatedTo(ChronoUnit.MICROS))
        .channelId(command.channelId())
        .build();
  }
}
