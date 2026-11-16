package com.devcool.application.service.channel;

import com.devcool.domain.channel.model.DirectChat;
import com.devcool.domain.channel.port.in.OpenDirectChatUseCase;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.channel.port.out.DirectChatPort;
import com.devcool.domain.user.port.out.UserPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class DirectChatService implements OpenDirectChatUseCase {
  private final UserPort userPort;
  private final ChannelPort channelPort;
  private final DirectChatPort directChatPort;
  private final TransactionOperations transactions;

  public DirectChatService(
      UserPort userPort,
      ChannelPort channelPort,
      DirectChatPort directChatPort,
      TransactionOperations transactions) {
    this.userPort = userPort;
    this.channelPort = channelPort;
    this.directChatPort = directChatPort;
    this.transactions = transactions;
  }

  @Override
  public DirectChat openDirectChat(OpenDirectChatCommand command) {
    throw new UnsupportedOperationException();
  }
}
