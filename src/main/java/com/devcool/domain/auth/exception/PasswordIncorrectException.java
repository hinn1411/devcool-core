package com.devcool.domain.auth.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class PasswordIncorrectException extends DomainException {
  public PasswordIncorrectException() {
    super(ErrorCode.PASSWORD_INCORRECT, "Password is incorrect", Map.of());
  }
}
