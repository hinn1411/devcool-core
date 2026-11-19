package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.command.SearchChannelMessagesCommand;
import com.devcool.domain.chat.port.in.command.SearchMyMessagesCommand;

/** Keyword search over live TEXT/MARKDOWN messages, newest first (P3-T13). */
public interface SearchMessagesQuery {
  /** One channel the caller is a member of; next page via {@code beforeSeq}. */
  MessageList searchInChannel(SearchChannelMessagesCommand command);

  /** Every channel the caller is a member of now; next page via {@code beforeId}. */
  MessageList searchMyChannels(SearchMyMessagesCommand command);
}
