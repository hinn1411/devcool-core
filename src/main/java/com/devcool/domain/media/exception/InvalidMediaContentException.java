package com.devcool.domain.media.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class InvalidMediaContentException extends DomainException {
  public InvalidMediaContentException() {
    super(ErrorCode.INVALID_MEDIA_CONTENT, "Media content is invalid", Map.of());
  }
}
