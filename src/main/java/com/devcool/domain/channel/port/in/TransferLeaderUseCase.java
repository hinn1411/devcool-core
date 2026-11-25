package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.port.in.command.TransferLeaderCommand;

public interface TransferLeaderUseCase {
  void transferLeader(TransferLeaderCommand command);
}
