package com.devcool.adapters.in.websocket.dto.v2;

/** One client reaction per code (ws-protocol skill). */
public enum NackCode {
  UNSUPPORTED,
  INVALID,
  FORBIDDEN,
  NOT_FOUND,
  CONFLICT,
  RATE_LIMITED,
  INTERNAL
}
