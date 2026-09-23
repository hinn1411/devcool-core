package com.devcool.adapters.in.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.common.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class HttpErrorMapperTest {

  @ParameterizedTest(name = "domainCode: {0}, expected: {1}")
  @CsvSource({
    "USER_NOT_FOUND, NOT_FOUND",
    "EMAIL_ALREADY_USED, CONFLICT",
    "PASSWORD_WEAK, UNPROCESSABLE_ENTITY",
    "CHANNEL_NOT_FOUND, NOT_FOUND",
    "MEMBER_NOT_FOUND, FORBIDDEN", // User is not a member of channel, must be forbidden
    "TOO_LARGE_MEDIA, PAYLOAD_TOO_LARGE",
    "USER_DUPLICATE, UNPROCESSABLE_ENTITY",
    "USERNAME_ALREADY_USED, CONFLICT",
    "PASSWORD_INCORRECT, UNAUTHORIZED",
    "PASSWORD_NOT_MATCH, UNPROCESSABLE_ENTITY",
    "REFRESH_TOKEN_INVALID, UNAUTHORIZED",
    "PASSWORD_DUPLICATE, UNPROCESSABLE_ENTITY",
    "VALIDATION_ERROR, UNPROCESSABLE_ENTITY",
    "INVALID_CHANNEL_CONFIG, BAD_REQUEST",
    "DUPLICATE_MEMBER, CONFLICT",
    "UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE",
    "INVALID_MEDIA_CONTENT, BAD_REQUEST",
    "INVALID_MESSAGE_CONFIG, BAD_REQUEST",
    "FORBIDDEN, FORBIDDEN",
    "OK, OK",
    "CREATED, CREATED",
    "INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR"
  })
  void toHttpStatus_definedErrorCodes_returnsExpectedStatus(
      ErrorCode domainCode, HttpStatus expected) {
    HttpStatus mappedHttpCode = HttpErrorMapper.toHttpStatus(domainCode);
    assertThat(mappedHttpCode).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} is client error")
  @EnumSource(
      value = ErrorCode.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"OK", "CREATED", "INTERNAL_SERVER_ERROR"})
  void toHttpStatus_clientErrorsAre4xx(ErrorCode errorCode) {
    HttpStatus mappedHttpCode = HttpErrorMapper.toHttpStatus(errorCode);
    assertThat(mappedHttpCode.value()).as("HTTP status for %s", errorCode).isBetween(400, 499);
  }
}
