package com.devcool.domain.auth.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

public class RefreshTokenInvalidException extends DomainException {
  public RefreshTokenInvalidException(String message) {
    super(ErrorCode.REFRESH_TOKEN_INVALID, message, null);
  }
}
