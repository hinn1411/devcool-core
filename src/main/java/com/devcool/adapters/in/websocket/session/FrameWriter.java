package com.devcool.adapters.in.websocket.session;

import com.devcool.adapters.in.websocket.dto.FrameType;
import com.devcool.adapters.in.websocket.dto.ServerFrame;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects the shared ObjectMapper; it is not owned state")
public class FrameWriter {

  private static final Logger log = LoggerFactory.getLogger(FrameWriter.class);

  private final ObjectMapper objectMapper;

  public void ack(WebSocketSession session, String clientMsgId, Object data) throws IOException {
    send(session, new ServerFrame(FrameType.ACK, clientMsgId, data));
  }

  public void error(WebSocketSession session, String clientMsgId, Object data) throws IOException {
    send(session, new ServerFrame(FrameType.ERROR, clientMsgId, data));
  }

  /** On overflow the decorator only throws; we close (4500). */
  public void send(WebSocketSession session, Object frame) throws IOException {
    TextMessage message = new TextMessage(objectMapper.writeValueAsString(frame));
    try {
      session.sendMessage(message);
    } catch (SessionLimitExceededException ex) {
      closeAsync(session, ex.getStatus());
    }
  }

  /** Off the caller's thread: Tomcat's close waits for the stuck writer's lock. */
  public void closeAsync(WebSocketSession session, CloseStatus status) {
    Thread.ofVirtual()
        .name("ws-close-", 0)
        .start(
            () -> {
              try {
                session.close(status);
              } catch (IOException ex) {
                log.debug("Close of {} failed", session.getId(), ex);
              }
            });
  }
}
