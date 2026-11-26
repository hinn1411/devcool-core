package com.devcool.adapters.in.websocket.dto.v2;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A v2 server frame (ADR-0005); absent fields are left out of the JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerEnvelope(int v, String type, String reqId, Long channelId, Object payload) {

  public static final int VERSION = 2;

  public static ServerEnvelope ack(String reqId, Object payload) {
    return new ServerEnvelope(VERSION, "ACK", reqId, null, payload);
  }

  public static ServerEnvelope nack(String reqId, NackCode code, String message) {
    return new ServerEnvelope(VERSION, "NACK", reqId, null, new ErrorPayload(code, message));
  }

  /** For a frame that couldn't be parsed into an envelope at all. */
  public static ServerEnvelope error(NackCode code, String message) {
    return new ServerEnvelope(VERSION, "ERROR", null, null, new ErrorPayload(code, message));
  }

  public static ServerEnvelope event(String type, Long channelId, Object payload) {
    return new ServerEnvelope(VERSION, type, null, channelId, payload);
  }
}
