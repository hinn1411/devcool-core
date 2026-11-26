package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import java.io.IOException;
import org.springframework.web.socket.WebSocketSession;

/** One v2 frame type; exceptions become NACKs in {@link FrameDispatcher}. */
public interface FrameHandler<P> {

  String type();

  Class<P> payloadType();

  void handle(WebSocketSession session, ClientEnvelope frame, P payload) throws IOException;
}
