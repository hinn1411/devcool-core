package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.EmptyPayload;
import com.devcool.adapters.in.websocket.dto.v2.ServerEnvelope;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** Client heartbeat (P4-T03): {@code PING} → {@code PONG} with the same reqId. */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class PingHandler implements FrameHandler<EmptyPayload> {

  private final FrameWriter writer;

  @Override
  public String type() {
    return "PING";
  }

  @Override
  public Class<EmptyPayload> payloadType() {
    return EmptyPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, EmptyPayload payload)
      throws IOException {
    writer.send(session, ServerEnvelope.pong(frame.reqId()));
  }
}
