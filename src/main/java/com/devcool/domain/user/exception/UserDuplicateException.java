package com.devcool.domain.user.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.List;
import java.util.Map;

public class UserDuplicateException extends DomainException {
  public UserDuplicateException(List<Integer> ids) {
    super(ErrorCode.USER_DUPLICATE, "User duplicate", Map.of("userIds", ids));
  }
}
