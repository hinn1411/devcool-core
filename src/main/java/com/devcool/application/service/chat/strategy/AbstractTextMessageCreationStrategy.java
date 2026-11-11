package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.policy.MessageContentPolicy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;

/** Base for types whose content is the message itself (TEXT, MARKDOWN). */
public abstract class AbstractTextMessageCreationStrategy extends AbstractMessageCreationStrategy {

  @Override
  public void validate(CreateMessageCommand command) {
    MessageContentPolicy.requireValidText(command.content());
  }
}
