package com.devcool.adapters.in.web.security;

import com.devcool.adapters.in.web.dto.wrapper.ApiErrorResponse;
import com.devcool.adapters.in.web.util.ApiResponseFactory;
import com.devcool.adapters.in.web.util.HttpErrorMapper;
import com.devcool.domain.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers a request that reached a protected endpoint without a valid access token. It runs in the
 * security filter chain, before any controller, so ApiExceptionHandler never sees these requests.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    HttpStatus unauthorizedStatus = HttpErrorMapper.toHttpStatus(ErrorCode.UNAUTHENTICATED);
    int unauthorizedCode = unauthorizedStatus.value();
    response.setStatus(unauthorizedCode);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiErrorResponse body =
        ApiResponseFactory.error(
            unauthorizedStatus, ErrorCode.UNAUTHENTICATED.code(), "Access token invalid", Map.of());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
