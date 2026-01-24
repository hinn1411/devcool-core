package com.devcool.domain.realtime.port.in;

import com.devcool.domain.realtime.port.in.command.SendMessageCommand;

public interface WsSendMessageUseCase {
  void sendMessage(SendMessageCommand command);
}
