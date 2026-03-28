package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

public class InvalidMessageConfigException extends DomainException {
  public InvalidMessageConfigException(String message) {
    super(ErrorCode.INVALID_MESSAGE_CONFIG, message, null);
  }
}
