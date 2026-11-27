package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.MarkChannelReadCommand;

/** Moves the caller's read marker forward in a channel (P3-T10). */
public interface MarkChannelReadUseCase {
  /** Returns the marker after the call. */
  long markRead(MarkChannelReadCommand command);
}
