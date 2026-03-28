package com.devcool.domain.chat.policy;

import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;

public interface MessageCreationStrategy {
  ContentType getSupportedType();

  Integer createMessage(CreateMessageCommand command);
}
