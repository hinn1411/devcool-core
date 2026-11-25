package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.command.SearchChannelMessagesCommand;
import com.devcool.domain.chat.port.in.command.SearchMyMessagesCommand;

/** Keyword search over live TEXT/MARKDOWN messages, newest first (P3-T13). */
public interface SearchMessagesQuery {
  MessageList searchInChannel(SearchChannelMessagesCommand command);

  MessageList searchMyChannels(SearchMyMessagesCommand command);
}
