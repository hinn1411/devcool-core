package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import java.util.function.Consumer;

public interface SendMessageUseCase {
  /**
   * Commit → {@code onCommitted} → push to the channel's other connections (the protocol's commit →
   * ACK → publish). On a retried key {@code onCommitted} gets the original and nothing is pushed.
   */
  void sendMessage(SendMessageCommand command, Consumer<SavedMessage> onCommitted);
}
