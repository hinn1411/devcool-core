package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.SaveMessageCommand;

public interface SaveMessageUseCase {
  Integer save(SaveMessageCommand command);
}
