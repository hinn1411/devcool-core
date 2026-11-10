package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** More than one history cursor in one request. */
public class InvalidHistoryCursorException extends DomainException {
  public InvalidHistoryCursorException(String message) {
    super(ErrorCode.INVALID_HISTORY_CURSOR, message, null);
  }
}
