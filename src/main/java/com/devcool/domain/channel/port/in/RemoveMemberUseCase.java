package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.RemoveMemberCommand;

public interface RemoveMemberUseCase {
  void removeMember(RemoveMemberCommand command);
}
