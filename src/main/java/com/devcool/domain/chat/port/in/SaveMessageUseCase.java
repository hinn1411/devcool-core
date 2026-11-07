package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;

public interface SaveMessageUseCase {
  /** Saves with the next seq, or returns the original for a retried {@code clientMsgId}. */
  SavedMessage save(CreateMessageCommand command);
}
