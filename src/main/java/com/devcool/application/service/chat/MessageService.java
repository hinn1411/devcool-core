package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.exception.InvalidHistoryCursorException;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.ReactionSummary;
import com.devcool.domain.chat.model.ReplyTarget;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class MessageService implements SaveMessageUseCase, GetMessageQuery {
  private static final Logger log = LoggerFactory.getLogger(MessageService.class);
  private final Map<ContentType, MessageCreationStrategy> creationStrategies;
  private final MessagePort messagePort;
  private final ReactionPort reactionPort;
  private final ChannelPort channelPort;
  private final ChannelAuthorizer channelAuthorizer;
  private final TransactionOperations transactions;

  public MessageService(
      List<MessageCreationStrategy> creationStrategies,
      MessagePort messagePort,
      ReactionPort reactionPort,
      ChannelPort channelPort,
      ChannelAuthorizer channelAuthorizer,
      TransactionOperations transactions) {
    this.creationStrategies = new EnumMap<>(ContentType.class);
    creationStrategies.forEach(
        strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.messagePort = messagePort;
    this.reactionPort = reactionPort;
    this.channelPort = channelPort;
    this.channelAuthorizer = channelAuthorizer;
    this.transactions = transactions;
  }

  /**
   * Not {@code @Transactional}: a unique violation aborts the tx, so the re-read needs a new one.
   */
  @Override
  public SavedMessage save(CreateMessageCommand command) {
    MessageCreationStrategy strategy = strategyFor(command.contentType());
    strategy.validate(command);
    channelAuthorizer.requireMembership(command.channelId(), command.userId());
    Optional<SavedMessage> saved =
        inTransaction(
            status -> {
              Optional<SavedMessage> attempt = saveOnce(strategy, command);
              if (attempt.isEmpty()) {
                status.setRollbackOnly(); // aborted by the unique violation; also undoes the bump
              }
              return attempt;
            });
    return saved.orElseGet(() -> rereadWinner(command));
  }

  /** The concurrent send that won the key race, read in a fresh transaction. */
  private SavedMessage rereadWinner(CreateMessageCommand command) {
    return inTransaction(status -> findReplay(command))
        .orElseThrow(
            () -> new IllegalStateException("Lost the clientMsgId race but no winner found"));
  }

  /** {@code execute} is {@code @Nullable}; our callbacks never return null. */
  private Optional<SavedMessage> inTransaction(TransactionCallback<Optional<SavedMessage>> work) {
    return Objects.requireNonNull(transactions.execute(work));
  }

  /** authz → replay → reply target → seq bump → insert. Empty if the insert lost the key race. */
  private Optional<SavedMessage> saveOnce(
      MessageCreationStrategy strategy, CreateMessageCommand command) {

    Optional<SavedMessage> replay = findReplay(command);
    if (replay.isPresent()) {
      return replay; // checked before the bump: a retry must not move last_seq
    }
    // After the replay: a retry of a reply whose parent was deleted since is still the duplicate.
    requireValidReplyTarget(command);

    // Row lock on the channel until commit (ADR-0012)
    long seq =
        channelPort
            .getNextSequence(command.channelId())
            .orElseThrow(() -> new ChannelNotFoundException(command.channelId()));
    Message message = strategy.buildMessage(command);
    message.setSeq(seq);
    message.setClientMsgId(command.clientMsgId());
    message.setReplyToId(command.replyToId());
    return messagePort
        .save(message)
        .map(id -> new SavedMessage(id, seq, message.getCreatedTime(), false));
  }

  /**
   * A reply's parent must exist, be in the same channel and not be deleted. All three failures give
   * the client the same answer (no oracle on which ids exist).
   */
  private void requireValidReplyTarget(CreateMessageCommand command) {
    if (Objects.isNull(command.replyToId())) {
      return;
    }
    ReplyTarget parent =
        messagePort
            .findReplyTarget(command.replyToId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "User {} replied to unknown message {} in channel {}",
                      command.userId(),
                      command.replyToId(),
                      command.channelId());
                  return new InvalidMessageConfigException("Invalid replyToId");
                });
    if (!Objects.equals(command.channelId(), parent.channelId())) {
      log.warn(
          "User {} replied in channel {} to message {} of channel {}",
          command.userId(),
          command.channelId(),
          command.replyToId(),
          parent.channelId());
      throw new InvalidMessageConfigException("Invalid replyToId");
    }
    if (Objects.nonNull(parent.deletedTime())) {
      // A normal race (deleted while the user typed), not a probe: INFO, not WARN.
      log.info(
          "User {} replied to deleted message {} in channel {}",
          command.userId(),
          command.replyToId(),
          command.channelId());
      throw new InvalidMessageConfigException("Invalid replyToId");
    }
  }

  /** The original send for this key; reuse in another channel is rejected. */
  private Optional<SavedMessage> findReplay(CreateMessageCommand command) {
    return messagePort
        .findByClientMsgId(command.userId(), command.clientMsgId())
        .map(
            original -> {
              if (!Objects.equals(original.channelId(), command.channelId())) {
                log.warn(
                    "User {} reused a clientMsgId of channel {} in channel {}",
                    command.userId(),
                    original.channelId(),
                    command.channelId());
                throw new ClientMsgIdReusedException();
              }
              return new SavedMessage(original.id(), original.seq(), original.createdTime(), true);
            });
  }

  private MessageCreationStrategy strategyFor(ContentType contentType) {
    MessageCreationStrategy strategy = creationStrategies.get(contentType);
    if (Objects.isNull(strategy)) {
      log.warn("No message creation strategy registered for contentType: {}", contentType);
      throw new InvalidMessageConfigException("Unsupported content type: " + contentType);
    }
    return strategy;
  }

  @Override
  @Transactional(readOnly = true)
  public MessageList getMessages(GetMessageCommand command) {
    // Request shape first: no DB read, and the answer doesn't reveal whether the channel exists.
    if (countCursors(command) > 1) {
      throw new InvalidHistoryCursorException("Use only one of cursorId, beforeSeq, afterSeq");
    }
    channelAuthorizer.requireMember(command.channelId(), command.userId());

    int fetchSize = command.limit() + 1; // one extra row tells whether another page exists
    List<MessageItem> fetched = queryMessages(command, fetchSize);
    boolean hasMore = fetched.size() > command.limit();
    List<MessageItem> items = hasMore ? fetched.subList(0, command.limit()) : fetched;
    attachReactions(items, command.userId());
    return new MessageList(items, hasMore ? items.getLast().getId() : null, hasMore);
  }

  /** One query per page (no N+1); tombstones aren't asked for and show none. */
  private void attachReactions(List<MessageItem> items, Long viewerId) {
    List<Long> liveIds =
        items.stream()
            .filter(item -> item.getDeletedTime() == null)
            .map(MessageItem::getId)
            .toList();
    Map<Long, List<ReactionSummary>> byMessage = reactionPort.summarize(liveIds, viewerId);
    for (MessageItem item : items) {
      item.setReactions(
          item.getDeletedTime() != null
              ? List.of()
              : byMessage.getOrDefault(item.getId(), List.of()));
    }
  }

  private static long countCursors(GetMessageCommand command) {
    return Stream.of(command.cursorId(), command.beforeSeq(), command.afterSeq())
        .filter(Objects::nonNull)
        .count();
  }

  /** No cursor → the newest page (findBeforeSeq with null). */
  private List<MessageItem> queryMessages(GetMessageCommand command, int fetchSize) {
    if (Objects.nonNull(command.cursorId())) {
      return messagePort.findMessages(command.channelId(), command.cursorId(), fetchSize);
    }
    if (Objects.nonNull(command.afterSeq())) {
      return messagePort.findAfterSeq(command.channelId(), command.afterSeq(), fetchSize);
    }
    return messagePort.findBeforeSeq(command.channelId(), command.beforeSeq(), fetchSize);
  }
}
