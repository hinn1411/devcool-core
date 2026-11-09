package com.devcool.application.service.chat;

import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WsSendMessageService implements SendMessageUseCase {

  private static final Logger log = LoggerFactory.getLogger(WsSendMessageService.class);
  private final ConnectionRegistryPort connectionRegistryPort;
  private final RealtimeEmitterPort realtimeEmitterPort;
  private final SaveMessageUseCase saveMessageUseCase;

  @Override
  public SavedMessage sendMessage(SendMessageCommand command) {
    // Validate channel status later

    CreateMessageCommand saveCommand = toSaveMessageCommand(command);
    SavedMessage saved = saveMessageUseCase.save(saveCommand);
    if (saved.duplicate()) {
      // A retry: the original send already published it. Push is at-most-once (design §4);
      // a client that missed it catches up by seq.
      return null;
    }

    Set<String> connections = connectionRegistryPort.getConnectionsByChannel(command.channelId());

    var payload =
        new OutboundWsEvent(
            "MESSAGE",
            command.channelId(),
            command.userId(),
            command.contentType().name(),
            command.content(),
            null,
            null,
            null);

    String currentConnectionId = command.connectionId();
    for (String connectionId : connections) {
      if (!Objects.equals(currentConnectionId, connectionId)) {
        realtimeEmitterPort.sendMessageToConnection(connectionId, payload);
      }
    }
    return null;
  }

  private CreateMessageCommand toSaveMessageCommand(SendMessageCommand sendCommand) {
    return new CreateMessageCommand(
        sendCommand.content(),
        sendCommand.contentType(),
        sendCommand.channelId(),
        sendCommand.userId(),
        sendCommand.clientMsgId());
  }

  public record OutboundWsEvent(
      String type,
      Long channelId,
      Long senderId,
      String contentType,
      String content,
      Long messageId,
      Long seq,
      Instant createdTime) {}
}
