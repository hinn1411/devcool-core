package com.devcool.adapters.web.util;

import com.devcool.domain.common.ErrorCode;
import org.springframework.http.HttpStatus;

public class HttpErrorMapper {
  private HttpErrorMapper() {}

  public static HttpStatus toHttpStatus(ErrorCode code) {
    return switch (code) {
      case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case EMAIL_ALREADY_USED -> HttpStatus.CONFLICT;
      case PASSWORD_WEAK -> HttpStatus.UNPROCESSABLE_ENTITY;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
