package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;

public interface SendMessageUseCase {
  /** On a retried key: the original. */
  SavedMessage sendMessage(SendMessageCommand command);
}
