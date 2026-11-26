package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import com.devcool.adapters.in.websocket.dto.v2.SendPayload;
import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.devcool.domain.chat.port.in.SendMessageUseCase;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
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
    throw new UnsupportedOperationException();
  }
}
