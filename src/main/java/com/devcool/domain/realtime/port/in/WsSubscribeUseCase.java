package com.devcool.domain.realtime.port.in;

import com.devcool.domain.realtime.port.in.command.SubscribeCommand;

public interface WsSubscribeUseCase {
  void subscribe(SubscribeCommand command);
}
