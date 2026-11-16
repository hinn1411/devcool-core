package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;

/** The current FORUM leader hands the role to another member (P3-T11). */
public interface TransferLeaderUseCase {
  void transferLeader(TransferLeaderCommand command);
}
