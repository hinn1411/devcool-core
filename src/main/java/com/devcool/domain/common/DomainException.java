package com.devcool.domain.common;

import java.util.Map;
import java.util.Objects;

public class DomainException extends RuntimeException {
  private final ErrorCode errorCode;
  private final Map<String, Object> details;

  protected DomainException(ErrorCode errorCode, String message, Map<String, Object> details) {
    super(message);
    this.errorCode = errorCode;
    this.details = Objects.isNull(details) ? Map.of() : Map.copyOf(details);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
