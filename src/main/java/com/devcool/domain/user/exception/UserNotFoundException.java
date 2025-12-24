package com.devcool.domain.user.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class UserNotFoundException extends DomainException {
  public UserNotFoundException(Integer id) {
    super(ErrorCode.USER_NOT_FOUND, "User not found", Map.of("userId", id));
  }

  public UserNotFoundException(String username) {
    super(ErrorCode.USER_NOT_FOUND, "User not found", Map.of("userName", username));
  }

  public UserNotFoundException(List<Integer> ids) {
    super(ErrorCode.USER_NOT_FOUND, "Users not found", Map.of("userIds", ids));
  }

  public UserNotFoundException(Set<Integer> ids) {
    super(ErrorCode.USER_NOT_FOUND, "Users not found", Map.of("userIds", Optional.ofNullable(ids).stream().toList()));
  }
}
