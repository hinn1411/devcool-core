package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.LeaveChannelCommand;

/** The caller leaves a channel (P3-T11). A FORUM leader must transfer first. */
public interface LeaveChannelUseCase {
  void leave(LeaveChannelCommand command);
}
