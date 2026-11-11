package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.exception.MessageEditWindowClosedException;
import com.devcool.domain.chat.exception.MessageNotFoundException;
import com.devcool.domain.chat.exception.MessageVersionConflictException;
import com.devcool.domain.chat.model.EditableMessage;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.policy.MessageContentPolicy;
import com.devcool.domain.chat.port.in.EditMessageUseCase;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import com.devcool.domain.common.ForbiddenException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sender-only edit within 24 h, guarded by the client's version (phase-3 Design notes, P3-T06). */
@Service
public class EditMessageService implements EditMessageUseCase {
  static final Duration EDIT_WINDOW = Duration.ofHours(24);

  private final MessagePort messagePort;
  private final ChannelAuthorizer channelAuthorizer;
  private final Clock clock;

  public EditMessageService(
      MessagePort messagePort, ChannelAuthorizer channelAuthorizer, Clock clock) {
    this.messagePort = messagePort;
    this.channelAuthorizer = channelAuthorizer;
    this.clock = clock;
  }

  @Override
  @Transactional
  public EditedMessage edit(EditMessageCommand command) {
    EditableMessage message =
        messagePort
            .findForEdit(command.messageId())
            .orElseThrow(() -> new MessageNotFoundException(command.messageId()));
    if (!channelAuthorizer.isMember(message.channelId(), command.userId())) {
      throw new MessageNotFoundException(command.messageId());
    }
    if (!Objects.equals(command.userId(), message.senderId())) {
      throw new ForbiddenException("Only the sender can edit this message");
    }
    if (message.contentType() != ContentType.TEXT
        && message.contentType() != ContentType.MARKDOWN) {
      throw new InvalidMessageConfigException("Only TEXT and MARKDOWN messages can be edited");
    }
    Instant now = Instant.now(clock);
    if (!now.isBefore(message.createdTime().plus(EDIT_WINDOW))) {
      throw new MessageEditWindowClosedException();
    }
    MessageContentPolicy.requireValidText(command.content());
    // Null-safe: the WS EDIT frame (P4-T04) reaches this port without the REST @NotNull.
    if (!Objects.equals(command.version(), message.version())) {
      throw new MessageVersionConflictException();
    }
    if (command.content().equals(message.content())) {
      return result(message, message.content(), message.version(), message.editedTime());
    }
    if (!messagePort.updateContent(message.id(), message.version(), command.content(), now)) {
      throw new MessageVersionConflictException();
    }
    return result(message, command.content(), message.version() + 1, now);
  }

  private static EditedMessage result(
      EditableMessage message, String content, int version, Instant editedTime) {
    return new EditedMessage(
        message.id(),
        message.channelId(),
        message.seq(),
        message.contentType(),
        content,
        version,
        editedTime);
  }
}
