package com.devcool.adapters.in.websocket.handler.action;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.SendAck;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class SendMessageAction implements FrameAction {

  private final FrameWriter writer;
  private final SendMessageUseCase sendMessageUseCase;

  @Override
  public FrameType type() {
    return FrameType.SEND_MESSAGE;
  }

  @Override
  public void handle(WebSocketSession session, ClientFrame frame) throws IOException {
    UUID clientMsgId = parseClientMsgId(frame.clientMsgId());
    if (Objects.isNull(clientMsgId)) {
      // Don't reflect an arbitrary client string back.
      writer.error(session, null, "Invalid clientMsgId");
      return;
    }
    SavedMessage saved =
        sendMessageUseCase.sendMessage(
            new SendMessageCommand(
                session.getId(),
                AuthHandshakeInterceptor.userIdOf(session),
                frame.channelId(),
                frame.contentType(),
                frame.content(),
                clientMsgId,
                frame.replyToId()));
    writer.ack(
        session, clientMsgId.toString(), new SendAck(saved.id(), saved.seq(), saved.createdTime()));
  }

  /** The idempotency key in canonical UUID form, or null when it is missing or malformed. */
  private static UUID parseClientMsgId(String raw) {
    // UUID.fromString accepts short forms like "1-1-1-1-1"; only the 36-char form is a client key.
    if (Objects.isNull(raw) || raw.length() != 36) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
