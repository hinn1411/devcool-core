package com.devcool.adapters.in.websocket.handler.action;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.session.FrameWriter;
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
public class PingAction implements FrameAction {

  private final FrameWriter writer;

  @Override
  public FrameType type() {
    return FrameType.PING;
  }

  @Override
  public void handle(WebSocketSession session, ClientFrame frame) throws IOException {
    writer.ack(session, frame.clientMsgId(), "PONG");
  }
}
