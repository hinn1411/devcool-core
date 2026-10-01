package com.devcool.domain.common;

import java.util.Map;

public class ForbiddenException extends DomainException {
  public ForbiddenException(String message) {
    super(ErrorCode.FORBIDDEN, message, Map.of());
  }
}
