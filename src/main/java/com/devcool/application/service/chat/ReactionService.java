package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.port.in.ReactToMessageUseCase;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.chat.port.out.ReactionPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import org.springframework.stereotype.Service;

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
  public void react(ReactionCommand command) {
    throw new UnsupportedOperationException("P3-T09 Red");
  }

  @Override
  public void unreact(ReactionCommand command) {
    throw new UnsupportedOperationException("P3-T09 Red");
  }
}
