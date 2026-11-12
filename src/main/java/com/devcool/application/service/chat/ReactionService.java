package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.model.Emoji;
import com.devcool.domain.chat.model.ReplyTarget;
import com.devcool.domain.chat.port.in.ReactToMessageUseCase;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toggle reactions: emoji valid → message live → caller a member → own row only. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class ReactionService implements ReactToMessageUseCase {

  private final ReactionPort reactionPort;
  private final MessagePort messagePort;
  private final ChannelAuthorizer channelAuthorizer;
  private final Clock clock;

  public ReactionService(
      ReactionPort reactionPort,
      MessagePort messagePort,
      ChannelAuthorizer channelAuthorizer,
      Clock clock) {
    this.reactionPort = reactionPort;
    this.messagePort = messagePort;
    this.channelAuthorizer = channelAuthorizer;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void react(ReactionCommand command) {
    Emoji emoji = new Emoji(command.emoji());
    requireReactable(command);
    reactionPort.add(command.messageId(), command.userId(), emoji, Instant.now(clock));
  }

  @Override
  @Transactional
  public void unreact(ReactionCommand command) {
    Emoji emoji = new Emoji(command.emoji());
    requireReactable(command);
    reactionPort.remove(command.messageId(), command.userId(), emoji);
  }

  /** Unknown, deleted and not-your-channel all answer 404, so ids can't be probed. */
  private void requireReactable(ReactionCommand command) {
    ReplyTarget target =
        messagePort
            .findReplyTarget(command.messageId())
            .filter(message -> message.deletedTime() == null)
            .orElseThrow(() -> new MessageNotFoundException(command.messageId()));
    if (!channelAuthorizer.isMember(target.channelId(), command.userId())) {
      throw new MessageNotFoundException(command.messageId());
    }
  }
}
