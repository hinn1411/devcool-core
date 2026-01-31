package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;

public interface WsUnsubscribeUseCase {
  void unsubscribe(UnsubscribeCommand command);
}
