package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import java.util.function.Consumer;

public interface SendMessageUseCase {
  /** Commit → onCommitted (ACK) → push. A retried key gets the original and no push. */
  void sendMessage(SendMessageCommand command, Consumer<SavedMessage> onCommitted);
}
