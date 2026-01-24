package com.devcool.adapters.in.web.util;

import com.devcool.adapters.in.web.dto.wrapper.ApiErrorResponse;
import com.devcool.adapters.in.web.dto.wrapper.ApiSuccessResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class ApiResponseFactory {

  private ApiResponseFactory() {}

  public static <T> ApiSuccessResponse<T> success(
      HttpStatus status, String code, String message, T data) {
    return ApiSuccessResponse.<T>builder()
        .code(code)
        .message(message)
        .data(data)
        .timestamp(Instant.now())
        .status(status.value())
        .build();
  }

  public static ApiErrorResponse error(
      HttpStatus status, String code, String message, Map<String, Object> details) {
    return ApiErrorResponse.builder()
        .code(code)
        .message(message)
        .details(details)
        .timestamp(Instant.now())
        .status(status.value())
        .build();
  }
}
