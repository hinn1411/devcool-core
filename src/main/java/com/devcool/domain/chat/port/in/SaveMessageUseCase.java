package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;

public interface SaveMessageUseCase {
  /**
   * Saves the message with the channel's next seq, or returns the original when the sender already
   * sent this {@code clientMsgId} to the same channel (no new row, no seq bump).
   */
  SavedMessage save(CreateMessageCommand command);
}
