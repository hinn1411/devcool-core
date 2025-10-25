package com.devcool.adapters.web.handler;

import com.devcool.adapters.web.dto.wrapper.ApiErrorResponse;
import com.devcool.adapters.web.util.ApiResponseFactory;
import com.devcool.adapters.web.util.HttpErrorMapper;
import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex) {
        HttpStatus status = HttpErrorMapper.toHttpStatus(ex.getErrorCode());
        return ResponseEntity
                .status(status)
                .body(ApiResponseFactory.error(status, ex.getErrorCode().code(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of("field", err.getField(), "message", err.getDefaultMessage()))
                .toList();

        return ResponseEntity.unprocessableEntity().body(ApiResponseFactory.error(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ErrorCode.VALIDATION_ERROR.code(),
                        "Input validation failed",
                        Map.of("fields", fieldErrors)
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponseFactory.error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.code(),
                "Unexpected server error",
                Map.of("error", ex.getClass().getSimpleName())
        ));
    }
}
