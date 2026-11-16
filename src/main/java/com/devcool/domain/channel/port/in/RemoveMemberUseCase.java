package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.RemoveMemberCommand;

/** A channel admin removes another member (P3-T11). Their messages stay. */
public interface RemoveMemberUseCase {
  void removeMember(RemoveMemberCommand command);
}
