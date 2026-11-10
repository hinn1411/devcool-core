package com.devcool.application.service.chat;

import com.devcool.application.service.channel.ChannelAuthorizer;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.port.in.EditMessageUseCase;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import com.devcool.domain.chat.port.out.MessagePort;
import java.time.Clock;
import java.time.Duration;
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
    throw new UnsupportedOperationException();
  }
}
