package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** Blank or over-long search text. */
public class InvalidSearchQueryException extends DomainException {
  public InvalidSearchQueryException(String message) {
    super(ErrorCode.INVALID_SEARCH_QUERY, message, null);
  }
}
