package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.exception.InvalidSearchQueryException;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.port.in.SearchMessagesQuery;
import com.devcool.domain.chat.port.in.command.SearchChannelMessagesCommand;
import com.devcool.domain.chat.port.in.command.SearchMyMessagesCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validate → authorize → search. Global search filters membership in SQL. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class SearchMessagesService implements SearchMessagesQuery {
  static final int MAX_QUERY_LENGTH = 200;

  private final MessagePort messagePort;
  private final ReactionPort reactionPort;
  private final ChannelAuthorizer channelAuthorizer;

  @Override
  @Transactional(readOnly = true)
  public MessageList searchInChannel(SearchChannelMessagesCommand command) {
    requireValid(command.query());
    channelAuthorizer.requireMember(command.channelId(), command.userId());
    List<MessageItem> fetched =
        messagePort.searchInChannel(
            command.query(), command.channelId(), command.beforeSeq(), command.limit() + 1);
    return page(fetched, command.limit(), command.userId(), MessageItem::getSeq);
  }

  @Override
  @Transactional(readOnly = true)
  public MessageList searchMyChannels(SearchMyMessagesCommand command) {
    requireValid(command.query());
    List<MessageItem> fetched =
        messagePort.searchInMyChannels(
            command.query(), command.userId(), command.beforeId(), command.limit() + 1);
    return page(fetched, command.limit(), command.userId(), MessageItem::getId);
  }

  private static void requireValid(String query) {
    if (query == null || query.isBlank()) {
      throw new InvalidSearchQueryException("Search text must not be blank");
    }
    if (query.codePointCount(0, query.length()) > MAX_QUERY_LENGTH) {
      throw new InvalidSearchQueryException(
          "Search text must be at most " + MAX_QUERY_LENGTH + " characters");
    }
  }

  /** limit+1 rows → hasMore; cursor = the value the next request sends back. */
  private MessageList page(
      List<MessageItem> fetched, int limit, Long viewerId, Function<MessageItem, Long> cursorOf) {
    boolean hasMore = fetched.size() > limit;
    List<MessageItem> items = hasMore ? fetched.subList(0, limit) : fetched;
    attachReactions(items, viewerId);
    return new MessageList(items, hasMore ? cursorOf.apply(items.getLast()) : null, hasMore);
  }

  /** One grouped query per page. */
  private void attachReactions(List<MessageItem> items, Long viewerId) {
    Map<Long, List<ReactionSummary>> byMessage =
        reactionPort.summarize(items.stream().map(MessageItem::getId).toList(), viewerId);
    items.forEach(item -> item.setReactions(byMessage.getOrDefault(item.getId(), List.of())));
  }
}
