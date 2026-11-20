package com.devcool.adapters.in.websocket.handler.action;

import com.devcool.adapters.in.websocket.dto.ClientFrame;
import com.devcool.adapters.in.websocket.dto.FrameType;
import java.io.IOException;
import org.springframework.web.socket.WebSocketSession;

/**
 * Handles one client {@link FrameType}: frame → command → inbound port → reply. A rejected
 * command's {@code DomainException} propagates; the handler turns it into an ERROR frame.
 */
public interface FrameAction {

  FrameType type();

  void handle(WebSocketSession session, ClientFrame frame) throws IOException;
}
