package com.devcool.domain.auth.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class InvalidCredentialsException extends DomainException {
  public InvalidCredentialsException() {
    super(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password", Map.of());
  }
}
