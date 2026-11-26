package com.devcool.adapters.in.websocket.dto.v2;

/** A v2 server frame (ADR-0005). */
public record ServerEnvelope(int v, String type, String reqId, Long channelId, Object payload) {

  public static ServerEnvelope ack(String reqId, Object payload) {
    throw new UnsupportedOperationException();
  }

  public static ServerEnvelope nack(String reqId, NackCode code, String message) {
    throw new UnsupportedOperationException();
  }

  /** For a frame that couldn't be parsed into an envelope at all. */
  public static ServerEnvelope error(NackCode code, String message) {
    throw new UnsupportedOperationException();
  }

  public static ServerEnvelope event(String type, Long channelId, Object payload) {
    throw new UnsupportedOperationException();
  }
}
