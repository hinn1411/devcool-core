package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.session.FrameWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** Routes a v2 frame to its {@link FrameHandler}; every failure becomes one NACK (ADR-0005). */
@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class FrameDispatcher {

  public FrameDispatcher(
      ObjectMapper objectMapper, FrameWriter writer, List<FrameHandler<?>> handlers) {}

  public void dispatch(WebSocketSession session, JsonNode frame) throws IOException {
    throw new UnsupportedOperationException();
  }
}
