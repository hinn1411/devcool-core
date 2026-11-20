package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;

public interface ConnectionLifecycleUseCase {
  void connect(ConnectCommand command);

  void disconnect(DisconnectCommand command);
}
