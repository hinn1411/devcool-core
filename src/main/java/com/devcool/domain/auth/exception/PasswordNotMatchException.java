package com.devcool.domain.auth.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class PasswordNotMatchException extends DomainException {
  public PasswordNotMatchException(String password) {
    super(ErrorCode.PASSWORD_NOT_MATCH, "Password is not match", Map.of("password", password));
  }
}
