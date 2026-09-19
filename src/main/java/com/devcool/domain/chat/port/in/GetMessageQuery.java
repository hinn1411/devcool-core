package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;

public interface GetMessageQuery {
  MessageList getMessages(GetMessageCommand command);
}
