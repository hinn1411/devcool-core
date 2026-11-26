package com.devcool.adapters.in.websocket.session;

import org.springframework.web.socket.WebSocketSession;

/** The protocol version of a connection's latest frame; pushes follow it. */
public final class ProtocolVersions {

  private ProtocolVersions() {}

  public static void mark(WebSocketSession session, int version) {
    throw new UnsupportedOperationException();
  }

  public static boolean isV2(WebSocketSession session) {
    throw new UnsupportedOperationException();
  }
}
