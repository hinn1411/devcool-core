package com.devcool.domain.chat.model;

import com.devcool.domain.chat.exception.InvalidClientMsgIdException;
import java.util.Objects;
import java.util.UUID;

/**
 * The sender's idempotency key for one message: a retry with the same key saves nothing. Only the
 * canonical 36-character UUID form is a key.
 */
public record ClientMsgId(UUID value) {

  private static final int CANONICAL_LENGTH = 36;

  public ClientMsgId {
    Objects.requireNonNull(value, "value");
  }

  /**
   * @throws InvalidClientMsgIdException when {@code raw} is null or not a canonical UUID
   */
  public static ClientMsgId parse(String raw) {
    // UUID.fromString accepts short forms like "1-1-1-1-1"; only the 36-char form is a client key.
    if (Objects.isNull(raw) || raw.length() != CANONICAL_LENGTH) {
      throw new InvalidClientMsgIdException();
    }
    try {
      return new ClientMsgId(UUID.fromString(raw));
    } catch (IllegalArgumentException ex) {
      throw new InvalidClientMsgIdException();
    }
  }

  /** The canonical lower-case form. */
  @Override
  public String toString() {
    return value.toString();
  }
}
