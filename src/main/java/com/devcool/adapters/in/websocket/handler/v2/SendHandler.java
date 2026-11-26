package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.SendAckPayload;
import com.devcool.adapters.in.websocket.dto.v2.SendPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
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
public class SendHandler implements FrameHandler<SendPayload> {

  private final FrameWriter writer;
  private final SendMessageUseCase sendMessageUseCase;

  @Override
  public String type() {
    return "SEND";
  }

  @Override
  public Class<SendPayload> payloadType() {
    return SendPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, SendPayload payload)
      throws IOException {
    ClientMsgId clientMsgId = ClientMsgId.parse(payload.clientMsgId());
    sendMessageUseCase.sendMessage(
        new SendMessageCommand(
            session.getId(),
            AuthHandshakeInterceptor.userIdOf(session),
            ChannelIds.require(frame),
            payload.contentType(),
            payload.content(),
            clientMsgId,
            payload.replyToId()),
        saved -> ack(session, frame.reqId(), clientMsgId, saved));
  }

  /** Called after commit; a failed write becomes NACK INTERNAL. */
  private void ack(
      WebSocketSession session, String reqId, ClientMsgId clientMsgId, SavedMessage saved) {
    try {
      writer.send(
          session,
          ServerEnvelope.ack(
              reqId,
              new SendAckPayload(
                  clientMsgId.toString(), saved.id(), saved.seq(), saved.createdTime())));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
