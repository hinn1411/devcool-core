package com.devcool.domain.chat.model;

import com.devcool.domain.chat.exception.InvalidClientMsgIdException;
import java.util.Objects;
import java.util.UUID;

/** The sender's idempotency key: a canonical 36-char UUID. */
public record ClientMsgId(UUID value) {

  private static final int CANONICAL_LENGTH = 36;

  public ClientMsgId {
    Objects.requireNonNull(value, "value");
  }

  public static ClientMsgId parse(String raw) {
    // UUID.fromString also accepts short forms like "1-1-1-1-1".
    if (Objects.isNull(raw) || raw.length() != CANONICAL_LENGTH) {
      throw new InvalidClientMsgIdException();
    }
    try {
      return new ClientMsgId(UUID.fromString(raw));
    } catch (IllegalArgumentException ex) {
      throw new InvalidClientMsgIdException();
    }
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
