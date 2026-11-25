package com.devcool.adapters.in.websocket.handler.action;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import java.io.IOException;
import org.springframework.web.socket.WebSocketSession;

/** Handles one client {@link FrameType}. A DomainException propagates to the handler. */
public interface FrameAction {

  FrameType type();

  void handle(WebSocketSession session, ClientFrame frame) throws IOException;
}
