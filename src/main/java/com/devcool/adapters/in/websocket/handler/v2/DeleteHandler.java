package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.DeletePayload;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.port.in.DeleteMessageUseCase;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** v2 {@code DELETE} (P4-T04). */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class DeleteHandler implements FrameHandler<DeletePayload> {

  private final FrameWriter writer;
  private final DeleteMessageUseCase deleteMessageUseCase;

  @Override
  public String type() {
    return "DELETE";
  }

  @Override
  public Class<DeletePayload> payloadType() {
    return DeletePayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, DeletePayload payload)
      throws IOException {
    throw new UnsupportedOperationException("P4-T04 Red");
  }
}
