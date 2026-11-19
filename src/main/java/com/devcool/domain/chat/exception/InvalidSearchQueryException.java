package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** Search text blank or too long; the text itself is not echoed back. */
public class InvalidSearchQueryException extends DomainException {
  public InvalidSearchQueryException(String message) {
    super(ErrorCode.INVALID_SEARCH_QUERY, message, null);
  }
}
