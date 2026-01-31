package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.SubscribeCommand;

public interface WsSubscribeUseCase {
  void subscribe(SubscribeCommand command);
}
