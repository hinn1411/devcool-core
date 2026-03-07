package com.devcool.domain.media.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class MediaTooLargeException extends DomainException {
  public MediaTooLargeException(long mediaSize) {
    super(ErrorCode.TOO_LARGE_MEDIA, "Media is too large", Map.of("mediaSize", mediaSize));
  }
}
