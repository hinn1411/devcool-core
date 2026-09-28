package com.devcool.adapters.in.web.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.web.dto.wrapper.ApiErrorResponse;
import com.devcool.domain.common.ErrorCode;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void handleDataIntegrityViolation_memberConstraintByName_returns409DuplicateMember() {
    SQLException sql =
        new SQLException(
            "duplicate key value violates unique constraint \"uk_member_channel_user\"");
    ConstraintViolationException cve =
        new ConstraintViolationException(
            "could not execute statement", sql, "uk_member_channel_user");
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("could not execute statement", cve);

    ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.DUPLICATE_MEMBER.code());
  }

  @Test
  void handleDataIntegrityViolation_memberConstraintByMessageFallback_returns409DuplicateMember() {
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_member_channel_user\"");

    ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.DUPLICATE_MEMBER.code());
  }

  @Test
  void handleDataIntegrityViolation_unrelatedViolation_returns500() {
    SQLException sql =
        new SQLException("null value in column \"name\" violates not-null constraint");
    DataIntegrityViolationException ex =
        new DataIntegrityViolationException("could not execute statement", sql);

    ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.code());
  }

  @Test
  void handleForbiddenRequest_returns403Forbidden() {
    ResponseEntity<ApiErrorResponse> response =
        handler.handleForbiddenRequest(new RuntimeException("Access Denied"));

    assertThat(response.getStatusCode().value()).isEqualTo(403);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.FORBIDDEN.code());
  }
}
