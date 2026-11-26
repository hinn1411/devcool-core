package com.devcool.adapters.in.websocket.handler.v2;

/** A missing or bad field; becomes {@code NACK{INVALID}}. */
public class InvalidFrameException extends RuntimeException {

  public InvalidFrameException(String message) {
    super(message);
  }
}
