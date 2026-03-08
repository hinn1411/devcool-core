package com.devcool.domain.media.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

public class InvalidObjectKeyException extends DomainException {
  public InvalidObjectKeyException() {
    super(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Object key is invalid", Map.of());
  }
}
