package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.devcool.domain.user.port.in.command.ChangePasswordCommand;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice: the real controller, exception handler and Jackson, with the use cases mocked.
 * Security filters are off, so the caller is supplied as the request principal where an endpoint
 * needs one; authentication itself is covered by the integration tests in P1-T12.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthDtoMapperImpl.class)
class AuthControllerTest {

  private static final Principal CALLER = new TestingAuthenticationToken("7", null);
  private static final String SUBMITTED_PASSWORD = "not-my-real-password";
  private static final String NEW_PASSWORD = "not-my-new-password";
  private static final String LOGIN_BODY =
      """
      {"username": "hien_giang", "password": "%s"}
      """
          .formatted(SUBMITTED_PASSWORD);
  private static final String CHANGE_PASSWORD_BODY =
      """
      {"currentPassword": "%s", "newPassword": "%s", "confirmedPassword": "%s"}
      """
          .formatted(SUBMITTED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

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

  // What AuthenticateUserService throws for an unknown user or a wrong password.
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

  @Test
  void changePassword_success_returns204ExpiresTheCookieAndPassesTheCallerId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_PASSWORD_BODY))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    allOf(containsString("rt=;"), containsString("Max-Age=0"))));

    ArgumentCaptor<ChangePasswordCommand> command =
        ArgumentCaptor.forClass(ChangePasswordCommand.class);
    verify(changePassword).change(command.capture());
    assertThat(command.getValue().userId()).isEqualTo(7);
    assertThat(command.getValue().currentPassword()).isEqualTo(SUBMITTED_PASSWORD);
    assertThat(command.getValue().newPassword()).isEqualTo(NEW_PASSWORD);
    assertThat(command.getValue().confirmedPassword()).isEqualTo(NEW_PASSWORD);
  }

  // A client cannot name whose password changes: a user id in the body is not part of the request.
  @Test
  void changePassword_ignoresAUserIdSentInTheBody() throws Exception {
    String bodyNamingAnotherUser =
        """
        {"userId": 1, "currentPassword": "%s", "newPassword": "%s", "confirmedPassword": "%s"}
        """
            .formatted(SUBMITTED_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyNamingAnotherUser))
        .andExpect(status().isNoContent());

    ArgumentCaptor<ChangePasswordCommand> command =
        ArgumentCaptor.forClass(ChangePasswordCommand.class);
    verify(changePassword).change(command.capture());
    assertThat(command.getValue().userId()).isEqualTo(7);
  }

  // Audit item #2: this exception used to carry the submitted password in its details.
  @Test
  void changePassword_wrongCurrentPassword_returns422AndEchoesNeitherPassword() throws Exception {
    doThrow(new PasswordIncorrectException()).when(changePassword).change(any());

    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CHANGE_PASSWORD_BODY))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("USR_223"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(content().string(not(containsString(SUBMITTED_PASSWORD))))
        .andExpect(content().string(not(containsString(NEW_PASSWORD))));
  }

  @Test
  void changePassword_tooShortNewPassword_returns422WithoutCallingTheUseCaseOrEchoingIt()
      throws Exception {
    String shortPassword = "short1";
    String body =
        """
        {"currentPassword": "%s", "newPassword": "%s", "confirmedPassword": "%s"}
        """
            .formatted(SUBMITTED_PASSWORD, shortPassword, shortPassword);

    mockMvc
        .perform(
            post("/api/v1/auth/password")
                .principal(CALLER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VLD_401"))
        .andExpect(content().string(not(containsString(SUBMITTED_PASSWORD))))
        .andExpect(content().string(not(containsString(shortPassword))));

    verifyNoInteractions(changePassword);
  }
}
