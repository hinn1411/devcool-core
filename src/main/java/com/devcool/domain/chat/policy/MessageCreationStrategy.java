package com.devcool.domain.chat.policy;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;

/** Builds the message for one content type. Authz, seq and persistence stay in the service. */
public interface MessageCreationStrategy {
  ContentType getSupportedType();

  /** Rejects bad content before any port call; no-op unless the type has content rules. */
  default void validate(CreateMessageCommand command) {}

  Message buildMessage(CreateMessageCommand command);
}
