package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.ServerFrame;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** The one place that turns a frame into JSON on the wire. */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the shared ObjectMapper; it is not owned state")
public class FrameWriter {

  private final ObjectMapper objectMapper;

  public void ack(WebSocketSession session, String clientMsgId, Object data) throws IOException {
    send(session, new ServerFrame(FrameType.ACK, clientMsgId, data));
  }

  public void error(WebSocketSession session, String clientMsgId, Object data) throws IOException {
    send(session, new ServerFrame(FrameType.ERROR, clientMsgId, data));
  }

  public void send(WebSocketSession session, Object frame) throws IOException {
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
  }
}
