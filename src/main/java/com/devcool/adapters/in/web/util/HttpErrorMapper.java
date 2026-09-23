package com.devcool.adapters.in.web.util;

import com.devcool.domain.common.ErrorCode;
import org.springframework.http.HttpStatus;

public class HttpErrorMapper {
  private HttpErrorMapper() {}

  public static HttpStatus toHttpStatus(ErrorCode code) {
    return switch (code) {
      case USER_NOT_FOUND, CHANNEL_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case EMAIL_ALREADY_USED, USERNAME_ALREADY_USED, DUPLICATE_MEMBER -> HttpStatus.CONFLICT;
      case PASSWORD_WEAK,
              PASSWORD_NOT_MATCH,
              PASSWORD_DUPLICATE,
              USER_DUPLICATE,
              VALIDATION_ERROR ->
          HttpStatus.UNPROCESSABLE_ENTITY;
      case INVALID_CHANNEL_CONFIG, INVALID_MEDIA_CONTENT, INVALID_MESSAGE_CONFIG ->
          HttpStatus.BAD_REQUEST;
      case PASSWORD_INCORRECT, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
      case MEMBER_NOT_FOUND, FORBIDDEN -> HttpStatus.FORBIDDEN;
      case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case TOO_LARGE_MEDIA -> HttpStatus.PAYLOAD_TOO_LARGE;
      case OK -> HttpStatus.OK;
      case CREATED -> HttpStatus.CREATED;
      case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
