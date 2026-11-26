package com.devcool.adapters.in.websocket.handler.v2;

import java.util.Objects;

/** A payload field the frame can't do without; missing → {@code NACK INVALID}. */
final class Required {

  private Required() {}

  static <T> T field(T value, String name) {
    if (Objects.isNull(value)) {
      throw new InvalidFrameException("Missing " + name);
    }
    return value;
  }
}
