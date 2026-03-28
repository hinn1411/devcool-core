package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.CreateMessageCommand;

public interface SaveMessageUseCase {
  Integer save(CreateMessageCommand command);
}
