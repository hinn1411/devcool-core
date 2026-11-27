package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.exception.ReactionLimitException;
import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.MessageRef;
import com.devcool.domain.chat.model.ReactionEvent;
import com.devcool.domain.chat.policy.ReactionPolicy;
import com.devcool.domain.chat.port.in.ReactToMessageUseCase;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order: emoji (400) → message live, caller a member (404) → cap (409) → own row. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class ReactionService implements ReactToMessageUseCase {

  private static final Logger log = LoggerFactory.getLogger(ReactionService.class);

  private final ReactionPort reactionPort;
  private final MessagePort messagePort;
  private final ChannelAuthorizer channelAuthorizer;
  private final Clock clock;
  private final ChannelEventPublisher eventPublisher;

  public ReactionService(
      ReactionPort reactionPort,
      MessagePort messagePort,
      ChannelAuthorizer channelAuthorizer,
      Clock clock,
      ChannelEventPublisher eventPublisher) {
    this.reactionPort = reactionPort;
    this.messagePort = messagePort;
    this.channelAuthorizer = channelAuthorizer;
    this.clock = clock;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public void react(ReactionCommand command) {
    Emoji emoji = new Emoji(command.emoji());
    Long channelId = requireReactable(command);
    // Not atomic: concurrent new emojis may overshoot the cap slightly.
    if (!ReactionPolicy.mayAdd(reactionPort.distinctEmojis(command.messageId()), emoji)) {
      throw new ReactionLimitException(ReactionPolicy.MAX_DISTINCT_EMOJIS);
    }
    if (reactionPort.add(command.messageId(), command.userId(), emoji, Instant.now(clock))) {
      publish(channelId, command, emoji, true);
    }
  }

  @Override
  @Transactional
  public void unreact(ReactionCommand command) {
    Emoji emoji = new Emoji(command.emoji());
    Long channelId = requireReactable(command);
    if (reactionPort.remove(command.messageId(), command.userId(), emoji)) {
      publish(channelId, command, emoji, false);
    }
  }

  private void publish(Long channelId, ReactionCommand command, Emoji emoji, boolean on) {
    eventPublisher.publishAfterCommit(
        new ReactionEvent(channelId, command.messageId(), emoji.value(), command.userId(), on),
        command.connectionId());
  }

  /** One 404 for unknown, deleted or not your channel; returns the channel id. */
  private Long requireReactable(ReactionCommand command) {
    MessageRef target =
        messagePort
            .findMessageRef(command.messageId())
            .filter(message -> message.deletedTime() == null)
            .orElseThrow(() -> new MessageNotFoundException(command.messageId()));
    if (!channelAuthorizer.isMember(target.channelId(), command.userId())) {
      log.warn(
          "User {} reacted to message {} outside their channels",
          command.userId(),
          command.messageId());
      throw new MessageNotFoundException(command.messageId());
    }
    return target.channelId();
  }
}
