package com.devcool.adapters.in.websocket.dto.v2;

import com.fasterxml.jackson.annotation.JsonInclude;

/** v2 server frame (ADR-0005); null fields are omitted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerEnvelope(int v, String type, String reqId, Long channelId, Object payload) {

  public static final int VERSION = 2;

  public static ServerEnvelope ack(String reqId, Object payload) {
    return new ServerEnvelope(VERSION, "ACK", reqId, null, payload);
  }

  public static ServerEnvelope nack(String reqId, NackCode code, String message) {
    return new ServerEnvelope(VERSION, "NACK", reqId, null, new ErrorPayload(code, message));
  }

  /** The heartbeat reply (P4-T03): PING's one reply, in place of an ACK. */
  public static ServerEnvelope pong(String reqId) {
    return new ServerEnvelope(VERSION, "PONG", reqId, null, null);
  }

  /** For a frame with no parsable envelope. */
  public static ServerEnvelope error(NackCode code, String message) {
    return new ServerEnvelope(VERSION, "ERROR", null, null, new ErrorPayload(code, message));
  }

  public static ServerEnvelope event(String type, Long channelId, Object payload) {
    return new ServerEnvelope(VERSION, type, null, channelId, payload);
  }
}
