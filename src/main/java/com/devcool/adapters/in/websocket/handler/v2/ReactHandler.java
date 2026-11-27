package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.ReactPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.port.in.ReactToMessageUseCase;
import com.devcool.domain.chat.port.in.command.ReactionCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** v2 {@code REACT}. */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ReactHandler implements FrameHandler<ReactPayload> {

  private final FrameWriter writer;
  private final ReactToMessageUseCase reactToMessageUseCase;

  @Override
  public String type() {
    return "REACT";
  }

  @Override
  public Class<ReactPayload> payloadType() {
    return ReactPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, ReactPayload payload)
      throws IOException {
    ReactionCommand command =
        new ReactionCommand(
            session.getId(),
            AuthHandshakeInterceptor.userIdOf(session),
            Required.field(payload.messageId(), "messageId"),
            Required.field(payload.emoji(), "emoji"));
    if (Required.field(payload.on(), "on")) {
      reactToMessageUseCase.react(command);
    } else {
      reactToMessageUseCase.unreact(command);
    }
    writer.send(session, ServerEnvelope.ack(frame.reqId(), null));
  }
}
