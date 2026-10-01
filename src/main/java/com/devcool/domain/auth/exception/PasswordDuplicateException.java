package com.devcool.domain.auth.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class PasswordDuplicateException extends DomainException {
  public PasswordDuplicateException() {
    super(
        ErrorCode.PASSWORD_DUPLICATE,
        "New password must be different from the current password",
        Map.of());
  }
}
