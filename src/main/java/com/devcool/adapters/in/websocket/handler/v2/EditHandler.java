package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.EditAckPayload;
import com.devcool.adapters.in.websocket.dto.v2.EditPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.security.AuthHandshakeInterceptor;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.model.EditedMessage;
import com.devcool.domain.chat.port.in.EditMessageUseCase;
import com.devcool.domain.chat.port.in.command.EditMessageCommand;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** v2 {@code EDIT} (P4-T04). */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class EditHandler implements FrameHandler<EditPayload> {

  private final FrameWriter writer;
  private final EditMessageUseCase editMessageUseCase;

  @Override
  public String type() {
    return "EDIT";
  }

  @Override
  public Class<EditPayload> payloadType() {
    return EditPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, EditPayload payload)
      throws IOException {
    EditedMessage edited =
        editMessageUseCase.edit(
            new EditMessageCommand(
                session.getId(),
                AuthHandshakeInterceptor.userIdOf(session),
                Required.field(payload.messageId(), "messageId"),
                payload.content(),
                payload.expectedVersion()));
    writer.send(session, ServerEnvelope.ack(frame.reqId(), EditAckPayload.from(edited)));
  }
}
