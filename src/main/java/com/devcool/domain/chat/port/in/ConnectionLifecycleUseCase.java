package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.ConnectCommand;
import com.devcool.domain.chat.port.in.command.DisconnectCommand;

/** A live connection opening and closing. Disconnect also drops all its subscriptions. */
public interface ConnectionLifecycleUseCase {
  void connect(ConnectCommand command);

  void disconnect(DisconnectCommand command);
}
