package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class ReactionLimitException extends DomainException {
  public ReactionLimitException(int max) {
    super(ErrorCode.REACTION_LIMIT_REACHED, "Too many different reactions", Map.of("max", max));
  }
}
