package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.ReadPayload;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.channel.port.in.MarkChannelReadUseCase;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** v2 {@code READ} (P4-T04). */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ReadHandler implements FrameHandler<ReadPayload> {

  private final FrameWriter writer;
  private final MarkChannelReadUseCase markChannelReadUseCase;

  @Override
  public String type() {
    return "READ";
  }

  @Override
  public Class<ReadPayload> payloadType() {
    return ReadPayload.class;
  }

  @Override
  public void handle(WebSocketSession session, ClientEnvelope frame, ReadPayload payload)
      throws IOException {
    throw new UnsupportedOperationException("P4-T04 Red");
  }
}
