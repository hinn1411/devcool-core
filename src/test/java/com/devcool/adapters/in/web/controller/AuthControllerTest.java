package com.devcool.adapters.in.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.mapper.AuthDtoMapperImpl;
import com.devcool.domain.auth.exception.InvalidCredentialsException;
import com.devcool.domain.auth.exception.PasswordIncorrectException;
import com.devcool.domain.auth.port.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.port.in.LogoutUseCase;
import com.devcool.domain.auth.port.in.RefreshTokenUseCase;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.port.in.ChangePasswordUseCase;
import com.devcool.domain.user.port.in.GetUserQuery;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice: the real controller, exception handler and Jackson, with the use cases mocked.
 * Security filters are off because these tests are about the response body.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthDtoMapperImpl.class)
class AuthControllerTest {

  private static final String SUBMITTED_PASSWORD = "not-my-real-password";
  private static final String LOGIN_BODY =
      """
      {"username": "hien_giang", "password": "%s"}
      """
          .formatted(SUBMITTED_PASSWORD);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthenticateUserUseCase authenticate;
  @MockitoBean private RegisterUserUseCase registerUser;
  @MockitoBean private ChangePasswordUseCase changePassword;
  @MockitoBean private GetUserQuery userQuery;
  @MockitoBean private RefreshTokenUseCase tokenRefresher;
  @MockitoBean private LogoutUseCase tokenRevoker;

  // JwtAuthFilter is still created as a bean in the slice, so its ports need stand-ins.
  @MockitoBean private TokenIssuerPort tokenIssuer;
  @MockitoBean private LoadUserPort loadUser;

  // Audit item #2: this exception used to carry the submitted password in its details.
  @Test
  void login_wrongPassword_errorBodyDoesNotEchoTheSubmittedPassword() throws Exception {
    when(authenticate.login(any())).thenThrow(new PasswordIncorrectException());

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("USR_223"))
        .andExpect(content().string(not(containsString(SUBMITTED_PASSWORD))));
  }

  // What AuthenticateUserService throws today for an unknown user or a wrong password.
  @Test
  void login_invalidCredentials_errorBodyDoesNotEchoTheSubmittedPassword() throws Exception {
    when(authenticate.login(any())).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_401"))
        .andExpect(content().string(not(containsString(SUBMITTED_PASSWORD))));
  }
}
