package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.port.in.SearchMessagesQuery;
import com.devcool.domain.chat.port.in.command.SearchChannelMessagesCommand;
import com.devcool.domain.chat.port.in.command.SearchMyMessagesCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class SearchMessagesService implements SearchMessagesQuery {
  private final MessagePort messagePort;
  private final ReactionPort reactionPort;
  private final ChannelAuthorizer channelAuthorizer;

  @Override
  public MessageList searchInChannel(SearchChannelMessagesCommand command) {
    throw new UnsupportedOperationException("P3-T13 Green");
  }

  @Override
  public MessageList searchMyChannels(SearchMyMessagesCommand command) {
    throw new UnsupportedOperationException("P3-T13 Green");
  }
}
