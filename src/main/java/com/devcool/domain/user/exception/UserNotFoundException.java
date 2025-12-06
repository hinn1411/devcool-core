package com.devcool.domain.user.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class UserNotFoundException extends DomainException {
  public UserNotFoundException(Integer id) {
    super(ErrorCode.USER_NOT_FOUND, "User not found", Map.of("userId", id));
  }

  public UserNotFoundException(String username) {
    super(ErrorCode.USER_NOT_FOUND, "User not found", Map.of("userName", username));
  }
}
