package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.ClientMsgIdReusedException;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageList;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.GetMessageQuery;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.GetMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
  private final ChannelPort channelPort;
  private final ChannelAuthorizer channelAuthorizer;
  private final TransactionOperations transactions;

  public MessageService(
      List<MessageCreationStrategy> creationStrategies,
      MessagePort messagePort,
      ChannelPort channelPort,
      ChannelAuthorizer channelAuthorizer,
      TransactionOperations transactions) {
    this.creationStrategies = new EnumMap<>(ContentType.class);
    creationStrategies.forEach(
        strategy -> this.creationStrategies.put(strategy.getSupportedType(), strategy));
    this.messagePort = messagePort;
    this.channelPort = channelPort;
    this.channelAuthorizer = channelAuthorizer;
    this.transactions = transactions;
  }

  /**
   * Not {@code @Transactional}: each attempt gets its own transaction from {@link #transactions},
   * because after a unique-key violation Postgres aborts the transaction it happened in.
   */
  @Override
  public SavedMessage save(CreateMessageCommand command) {
    MessageCreationStrategy strategy = strategyFor(command.contentType());
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

  /** Our callbacks never return null; {@code execute} is {@code @Nullable} only in general. */
  private Optional<SavedMessage> inTransaction(TransactionCallback<Optional<SavedMessage>> work) {
    return Objects.requireNonNull(transactions.execute(work));
  }

  /**
   * One attempt, inside the caller's transaction: authz → key lookup → seq bump → insert. Empty
   * when the insert lost the race on the sender's key; the transaction is then aborted.
   */
  private Optional<SavedMessage> saveOnce(
      MessageCreationStrategy strategy, CreateMessageCommand command) {
    // Membership only, no channel-exists check first: a missing channel and a channel the caller
    // is not in must give the same answer, or SEND_MESSAGE becomes a channel-id oracle.
    channelAuthorizer.requireMembership(command.channelId(), command.userId());

    Optional<SavedMessage> replay = findReplay(command);
    if (replay.isPresent()) {
      return replay; // checked before the bump: a retry must not move last_seq
    }

    // Row lock on the channel until commit (ADR-0012)
    long seq =
        channelPort
            .getNextSequence(command.channelId())
            .orElseThrow(() -> new ChannelNotFoundException(command.channelId()));
    Message message = strategy.buildMessage(command);
    message.setSeq(seq);
    message.setClientMsgId(command.clientMsgId());
    return messagePort
        .save(message)
        .map(id -> new SavedMessage(id, seq, message.getCreatedTime(), false));
  }

  /**
   * The original message when the sender already used this key in this channel. The key is unique
   * per sender, not per channel, so reuse in another channel is a client bug: reject it rather than
   * silently drop the new message.
   */
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
    channelAuthorizer.requireMember(command.channelId(), command.userId());

    // Fetch one extra row to know whether another page exists.
    List<MessageItem> fetched =
        messagePort.findMessages(command.channelId(), command.cursorId(), command.limit() + 1);
    if (fetched.isEmpty()) {
      return new MessageList(List.of(), null, false);
    }

    boolean hasMore = fetched.size() > command.limit();
    List<MessageItem> items = hasMore ? fetched.subList(0, command.limit()) : fetched;
    return new MessageList(items, items.getLast().getId(), hasMore);
  }
}
