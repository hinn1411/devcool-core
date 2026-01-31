package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.SendMessageCommand;

public interface SendMessageUseCase {
  void sendMessage(SendMessageCommand command);
}
