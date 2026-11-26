package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import java.io.IOException;
import org.springframework.web.socket.WebSocketSession;

/**
 * Handles one v2 frame type. Exceptions propagate to {@link FrameDispatcher}, which turns each into
 * a NACK.
 */
public interface FrameHandler<P> {

  String type();

  Class<P> payloadType();

  void handle(WebSocketSession session, ClientEnvelope frame, P payload) throws IOException;
}
