package com.devcool.domain.realtime.port.in;

import com.devcool.domain.realtime.port.in.command.UnsubscribeCommand;

public interface WsUnsubscribeUseCase {
  void unsubscribe(UnsubscribeCommand command);
}
