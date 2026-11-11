package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.DeleteMessageCommand;

public interface DeleteMessageUseCase {
  /** Soft-deletes; deleting an already-deleted message is a no-op. */
  void delete(DeleteMessageCommand command);
}
