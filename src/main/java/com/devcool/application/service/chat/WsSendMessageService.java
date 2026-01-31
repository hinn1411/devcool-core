package com.devcool.application.service.chat;

import com.devcool.domain.chat.port.in.SaveMessageUseCase;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.SaveMessageCommand;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WsSendMessageService implements SendMessageUseCase {

  private static final Logger log = LoggerFactory.getLogger(WsSendMessageService.class);
  private final ConnectionRegistryPort connectionRegistryPort;
  private final RealtimeEmitterPort realtimeEmitterPort;
  private final SaveMessageUseCase saveMessageUseCase;

  @Override
  public void sendMessage(SendMessageCommand command) {
    // Validate channel status later

    SaveMessageCommand saveCommand = toSaveMessageCommand(command);
    saveMessageUseCase.save(saveCommand);

    Set<String> connections = connectionRegistryPort.getConnectionsByChannel(command.channelId());

    var payload = new OutboundWsEvent(
        "MESSAGE",
        command.channelId(),
        command.userId(),
        command.contentType().name(),
        command.content()
    );

    String currentConnectionId = command.connectionId();
    for (String connectionId : connections) {
      if (!Objects.equals(currentConnectionId, connectionId)) {
        realtimeEmitterPort.sendMessageToConnection(connectionId, payload);
      }
    }
  }

  private SaveMessageCommand toSaveMessageCommand(SendMessageCommand sendCommand) {
    return new SaveMessageCommand(
        sendCommand.content(),
        sendCommand.contentType(),
        sendCommand.channelId(),
        sendCommand.userId()
    );
  }

  public record OutboundWsEvent(
      String type,
      Integer channelId,
      Integer senderId,
      String contentType,
      String content
  ) {}
}
