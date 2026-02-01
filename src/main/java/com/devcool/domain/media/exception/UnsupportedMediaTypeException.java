package com.devcool.domain.media.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class UnsupportedMediaTypeException extends DomainException {
  public UnsupportedMediaTypeException(String mediaType) {
    super(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", Map.of("mediaType", mediaType));
  }
}
