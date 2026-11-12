package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

/** The reaction is not an emoji (or too long). The value is not echoed back. */
public class InvalidEmojiException extends DomainException {
  public InvalidEmojiException() {
    super(ErrorCode.INVALID_EMOJI, "Reaction must be an emoji", Map.of());
  }
}
