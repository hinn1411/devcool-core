package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;

public interface SendMessageUseCase {
  /** The saved message's ids; for a retried key, the original's. */
  SavedMessage sendMessage(SendMessageCommand command);
}
