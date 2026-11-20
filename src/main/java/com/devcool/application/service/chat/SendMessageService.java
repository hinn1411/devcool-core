package com.devcool.application.service.chat;

import com.devcool.domain.chat.model.MessageEvent;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Not {@code @Transactional}: {@link SaveMessageUseCase#save} commits in its own transaction, so
 * the message is durable before the ACK and the push.
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class SendMessageService implements SendMessageUseCase {

  private final ConnectionRegistryPort connectionRegistryPort;
  private final RealtimeEmitterPort realtimeEmitterPort;
  private final SaveMessageUseCase saveMessageUseCase;

  @Override
  public void sendMessage(SendMessageCommand command, Consumer<SavedMessage> onCommitted) {
    SavedMessage saved = saveMessageUseCase.save(toCreateCommand(command));
    try {
      onCommitted.accept(saved);
    } finally {
      // Committed is committed: a failed ACK (dead sender socket) must not hide the message from
      // the others. A retry is not pushed again; the original send already did (design §4).
      if (!saved.duplicate()) {
        publish(command, saved);
      }
    }
  }

  /** Pushes to every connection subscribed to the channel except the sender's own. */
  private void publish(SendMessageCommand command, SavedMessage saved) {
    MessageEvent event = toEvent(command, saved);
    for (String connectionId :
        connectionRegistryPort.getConnectionsByChannel(command.channelId())) {
      if (!Objects.equals(command.connectionId(), connectionId)) {
        realtimeEmitterPort.send(connectionId, event);
      }
    }
  }

  private static MessageEvent toEvent(SendMessageCommand command, SavedMessage saved) {
    return new MessageEvent(
        command.channelId(),
        command.userId(),
        command.contentType(),
        command.content(),
        saved.id(),
        saved.seq(),
        saved.createdTime(),
        command.replyToId());
  }

  private static CreateMessageCommand toCreateCommand(SendMessageCommand command) {
    return new CreateMessageCommand(
        command.content(),
        command.contentType(),
        command.channelId(),
        command.userId(),
        command.clientMsgId().value(),
        command.replyToId());
  }
}
