package com.devcool.application.service.chat.strategy;

import com.devcool.domain.channel.exception.ChannelNotFoundException;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.policy.MessageCreationStrategy;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.member.exception.MemberNotFoundException;
import com.devcool.domain.member.port.out.MemberPort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractMessageCreationStrategy implements MessageCreationStrategy {
  protected final ChannelPort channelPort;
  protected final MemberPort memberPort;
  protected final MessagePort messagePort;

  /**
   * Validates the channel and the sender's membership before delegating the message construction to
   * {@link #buildMessage(CreateMessageCommand)}. The channel existence is checked first so a
   * non-existent channel reports {@link ChannelNotFoundException} rather than being masked by the
   * membership lookup.
   */
  @Override
  public final Integer createMessage(CreateMessageCommand command) {
    requireChannelExists(command.channelId());
    requireMemberInChannel(command.channelId(), command.userId());

    return messagePort.save(buildMessage(command));
  }

  /**
   * Builds the message carrying only the sender and channel ids. The sender is not loaded: the
   * membership check above already proves the user exists, and the persistence adapter resolves
   * both foreign keys without a query.
   */
  protected Message buildMessage(CreateMessageCommand command) {
    return Message.builder()
        .senderId(command.userId())
        .content(command.content())
        .contentType(command.contentType())
        .createdTime(Instant.now())
        .channelId(command.channelId())
        .build();
  }

  private void requireChannelExists(Integer channelId) {
    if (!channelPort.existById(channelId)) {
      throw new ChannelNotFoundException(channelId);
    }
  }

  private void requireMemberInChannel(Integer channelId, Integer userId) {
    if (!memberPort.existMemberOfChannelByUserId(channelId, userId)) {
      throw new MemberNotFoundException(userId);
    }
  }
}
