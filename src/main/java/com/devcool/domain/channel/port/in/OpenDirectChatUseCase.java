package com.devcool.domain.channel.port.in;

import com.devcool.domain.channel.model.DirectChat;
import com.devcool.domain.channel.port.in.command.OpenDirectChatCommand;

/** Returns the caller's PRIVATE_CHAT with the target, creating it on first use. */
public interface OpenDirectChatUseCase {
  DirectChat openDirectChat(OpenDirectChatCommand command);
}
