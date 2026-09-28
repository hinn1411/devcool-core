package com.devcool.adapters.in.web.handler;

import com.devcool.adapters.in.web.dto.wrapper.ApiErrorResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.adapters.in.web.util.HttpErrorMapper;
import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex) {
    HttpStatus status = HttpErrorMapper.toHttpStatus(ex.getErrorCode());
    return ResponseEntity.status(status)
        .body(
            ApiResponseFactory.error(
                status, ex.getErrorCode().code(), ex.getMessage(), ex.getDetails()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                err ->
                    Map.of(
                        "field",
                        err.getField(),
                        "message",
                        Optional.ofNullable(err.getDefaultMessage()).orElse("")))
            .toList();

    return ResponseEntity.unprocessableEntity()
        .body(
            ApiResponseFactory.error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR.code(),
                "Input validation failed",
                Map.of("fields", fieldErrors)));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodValidation(
      HandlerMethodValidationException ex) {
    List<Map<String, String>> paramErrors =
        ex.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            err ->
                                Map.of(
                                    "field",
                                    Optional.ofNullable(
                                            result.getMethodParameter().getParameterName())
                                        .orElse(""),
                                    "message",
                                    Optional.ofNullable(err.getDefaultMessage()).orElse(""))))
            .toList();

    return ResponseEntity.unprocessableEntity()
        .body(
            ApiResponseFactory.error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.VALIDATION_ERROR.code(),
                "Input validation failed",
                Map.of("fields", paramErrors)));
  }

  /** Rejected by the servlet multipart limits before reaching a controller. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    HttpStatus status = HttpErrorMapper.toHttpStatus(ErrorCode.TOO_LARGE_MEDIA);
    return ResponseEntity.status(status)
        .body(
            ApiResponseFactory.error(
                status,
                ErrorCode.TOO_LARGE_MEDIA.code(),
                "Upload exceeds the allowed size",
                Map.of("maxSize", ex.getMaxUploadSize())));
  }

  /**
   * A duplicate member insert that races past the in-service pre-check fails the {@code
   * uk_member_channel_user} constraint at commit. Map only that violation to 409; any other
   * integrity violation keeps the generic 500.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {
    if (isMemberUniqueViolation(ex)) {
      HttpStatus status = HttpErrorMapper.toHttpStatus(ErrorCode.DUPLICATE_MEMBER);
      return ResponseEntity.status(status)
          .body(
              ApiResponseFactory.error(
                  status,
                  ErrorCode.DUPLICATE_MEMBER.code(),
                  "Member already in channel",
                  Map.of()));
    }
    return handleGeneric(ex);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
    return ResponseEntity.internalServerError()
        .body(
            ApiResponseFactory.error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.code(),
                "Unexpected server error",
                Map.of("error", ex.getClass().getSimpleName())));
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleForbiddenRequest(Exception ex) {
    HttpStatus status = HttpErrorMapper.toHttpStatus(ErrorCode.FORBIDDEN);
    return ResponseEntity.status(status)
        .body(
            ApiResponseFactory.error(
                status,
                ErrorCode.FORBIDDEN.code(),
                ex.getMessage(),
                Map.of("error", ex.getClass().getSimpleName())));
  }

  private static boolean isMemberUniqueViolation(DataIntegrityViolationException ex) {
    if (ex.getCause() instanceof ConstraintViolationException cve) {
      String name = cve.getConstraintName();
      if (name != null && name.toLowerCase(Locale.ROOT).contains("uk_member_channel_user")) {
        return true;
      }
    }
    Throwable root = ex.getMostSpecificCause();
    return root.getMessage() != null
        && root.getMessage().toLowerCase(Locale.ROOT).contains("uk_member_channel_user");
  }
}
