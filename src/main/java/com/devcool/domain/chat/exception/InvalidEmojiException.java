package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

/** Not an emoji; the value isn't echoed back. */
public class InvalidEmojiException extends DomainException {
  public InvalidEmojiException() {
    super(ErrorCode.INVALID_EMOJI, "Reaction must be an emoji", Map.of());
  }
}
