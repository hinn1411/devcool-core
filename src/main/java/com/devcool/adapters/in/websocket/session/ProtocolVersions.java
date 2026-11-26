package com.devcool.adapters.in.websocket.session;

import java.util.Map;
import java.util.Objects;
import org.springframework.web.socket.WebSocketSession;

/** Protocol version of the connection's latest frame; pushes follow it. */
public final class ProtocolVersions {

  static final String ATTR_VERSION = "devcool.ws.protocolVersion";

  private ProtocolVersions() {}

  public static void mark(WebSocketSession session, int version) {
    session.getAttributes().put(ATTR_VERSION, version);
  }

  /** No frame yet = v1; pushes start only after a SUBSCRIBE. */
  public static boolean isV2(WebSocketSession session) {
    Map<String, Object> attributes = session.getAttributes();
    return Objects.nonNull(attributes) && Integer.valueOf(2).equals(attributes.get(ATTR_VERSION));
  }
}
