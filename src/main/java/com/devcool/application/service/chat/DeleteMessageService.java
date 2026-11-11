package com.devcool.application.service.chat;

import com.devcool.domain.channel.model.enums.ChannelType;
import com.devcool.domain.channel.port.out.ChannelPort;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.model.DeletableMessage;
import com.devcool.domain.chat.policy.MessageDeletePolicy;
import com.devcool.domain.chat.port.in.DeleteMessageUseCase;
import com.devcool.domain.chat.port.in.command.DeleteMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.common.ForbiddenException;
import com.devcool.domain.member.model.enums.MemberType;
import com.devcool.domain.member.port.out.MemberPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Soft delete by the sender or a group-channel admin; idempotent. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the ports; they are shared stateless beans, not owned state")
public class DeleteMessageService implements DeleteMessageUseCase {

  private final MessagePort messagePort;
  private final MemberPort memberPort;
  private final ChannelPort channelPort;
  private final Clock clock;

  public DeleteMessageService(
      MessagePort messagePort, MemberPort memberPort, ChannelPort channelPort, Clock clock) {
    this.messagePort = messagePort;
    this.memberPort = memberPort;
    this.channelPort = channelPort;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void delete(DeleteMessageCommand command) {
    DeletableMessage message =
        messagePort
            .findForDelete(command.messageId())
            .orElseThrow(() -> new MessageNotFoundException(command.messageId()));
    MemberType role =
        memberPort
            .findRoleOfMember(message.channelId(), command.userId())
            .orElseThrow(() -> new MessageNotFoundException(command.messageId()));
    ChannelType channelType =
        channelPort
            .findAccessInfo(message.channelId())
            .orElseThrow(() -> new MessageNotFoundException(command.messageId()))
            .channelType();
    boolean isSender = Objects.equals(command.userId(), message.senderId());
    if (!MessageDeletePolicy.mayDelete(isSender, channelType, role)) {
      throw new ForbiddenException("Only the sender or a channel admin can delete this message");
    }
    if (message.deletedTime() == null) {
      messagePort.markDeleted(message.id(), Instant.now(clock));
    }
  }
}
