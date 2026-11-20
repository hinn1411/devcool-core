package com.devcool.adapters.in.websocket.handler.action;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.SendAck;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.exception.InvalidClientMsgIdException;
import com.devcool.domain.chat.model.ClientMsgId;
import com.devcool.domain.chat.model.SavedMessage;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import com.devcool.domain.chat.port.in.command.SendMessageCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.UncheckedIOException;
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
    ClientMsgId clientMsgId;
    try {
      clientMsgId = ClientMsgId.parse(frame.clientMsgId());
    } catch (InvalidClientMsgIdException ex) {
      // Handled here, not by the dispatcher: it would reflect the arbitrary client string back.
      writer.error(session, null, "Invalid clientMsgId");
      return;
    }
    sendMessageUseCase.sendMessage(
        new SendMessageCommand(
            session.getId(),
            AuthHandshakeInterceptor.userIdOf(session),
            frame.channelId(),
            frame.contentType(),
            frame.content(),
            clientMsgId,
            frame.replyToId()),
        saved -> ack(session, clientMsgId, saved));
  }

  /** Runs inside the use case, between commit and push; a dead socket must not lose the push. */
  private void ack(WebSocketSession session, ClientMsgId clientMsgId, SavedMessage saved) {
    try {
      writer.ack(
          session,
          clientMsgId.toString(),
          new SendAck(saved.id(), saved.seq(), saved.createdTime()));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
