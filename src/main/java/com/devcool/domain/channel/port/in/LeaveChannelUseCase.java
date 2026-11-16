package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;

public interface LeaveChannelUseCase {
  void leave(LeaveChannelCommand command);
}
