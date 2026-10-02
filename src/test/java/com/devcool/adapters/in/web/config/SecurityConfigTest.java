package com.devcool.adapters.in.web.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.controller.AuthController;
import com.devcool.adapters.in.web.controller.ChannelController;
import com.devcool.adapters.in.web.dto.mapper.AuthDtoMapperImpl;
import com.devcool.adapters.in.web.dto.mapper.ChannelDtoMapperImpl;
import com.devcool.adapters.in.web.security.ApiAuthenticationEntryPoint;
import com.devcool.domain.auth.exception.InvalidCredentialsException;
import com.devcool.domain.auth.port.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.port.in.LogoutUseCase;
import com.devcool.domain.auth.port.in.RefreshTokenUseCase;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.channel.port.in.CreateChannelUseCase;
import com.devcool.domain.channel.port.in.GetChannelQuery;
import com.devcool.domain.channel.port.in.UpdateChannelUseCase;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.in.ChangePasswordUseCase;
import com.devcool.domain.user.port.in.GetUserQuery;
import com.devcool.domain.user.port.in.RegisterUserUseCase;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Web slice with the real security filter chain: SecurityConfig, JwtAuthFilter and the entry point,
 * with the use cases and the filter's ports mocked. The other controller slices switch the filters
 * off, so this is where "who may call what without a token" is checked. Real tokens against a real
 * database are covered by the integration tests in P1-T12.
 */
@WebMvcTest({ChannelController.class, AuthController.class})
@Import({
  SecurityConfig.class,
  ApiAuthenticationEntryPoint.class,
  ChannelDtoMapperImpl.class,
  AuthDtoMapperImpl.class
})
class SecurityConfigTest {

  private static final Integer USER_ID = 7;
  private static final Integer TOKEN_VERSION = 1;
  private static final String REJECTED_TOKEN = "rejected-access-token";
  private static final String LOGIN_BODY =
      """
      {"username": "hien_giang", "password": "not-my-real-password"}
      """;
  private static final String CREATE_CHANNEL_BODY =
      """
      {"name": "room", "boundaryType": "PUBLIC", "channelType": "LOUNGE"}
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UpdateChannelUseCase channelUpdater;
  @MockitoBean private CreateChannelUseCase channelCreator;
  @MockitoBean private GetChannelQuery channelQuerier;
  @MockitoBean private AuthenticateUserUseCase authenticate;
  @MockitoBean private RegisterUserUseCase registerUser;
  @MockitoBean private ChangePasswordUseCase changePassword;
  @MockitoBean private GetUserQuery userQuery;
  @MockitoBean private RefreshTokenUseCase tokenRefresher;
  @MockitoBean private LogoutUseCase tokenRevoker;

  // The ports JwtAuthFilter authenticates with.
  @MockitoBean private TokenIssuerPort tokenIssuerPort;
  @MockitoBean private LoadUserPort loadUserPort;

  // JwtAuthFilter asks the port whether the token is valid, then reads the claims itself, so an
  // accepted token must be a correctly-shaped JWT.
  private String acceptedBearerToken() throws JOSEException {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(String.valueOf(USER_ID))
            .claim("version", TOKEN_VERSION)
            .claim("role", "USER")
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
    String token = jwt.serialize();

    when(tokenIssuerPort.isAccessTokenValid(token)).thenReturn(true);
    when(loadUserPort.loadById(USER_ID))
        .thenReturn(Optional.of(User.builder().id(USER_ID).tokenVersion(TOKEN_VERSION).build()));
    return "Bearer " + token;
  }

  private static void expectUnauthenticated(ResultActions result) throws Exception {
    result
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("AUTH_402"));
  }

  @Test
  void listChannels_noToken_returns401AndNeverReachesTheUseCase() throws Exception {
    expectUnauthenticated(mockMvc.perform(get("/api/v1/channels")));

    verifyNoInteractions(channelQuerier);
  }

  @Test
  void createChannel_noToken_returns401AndNeverReachesTheUseCase() throws Exception {
    expectUnauthenticated(
        mockMvc.perform(
            post("/api/v1/channels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_CHANNEL_BODY)));

    verifyNoInteractions(channelCreator);
  }

  @Test
  void logout_noToken_returns401AndNeverReachesTheUseCase() throws Exception {
    expectUnauthenticated(mockMvc.perform(post("/api/v1/auth/logout")));

    verifyNoInteractions(tokenRevoker);
  }

  @Test
  void listChannels_rejectedToken_returns401WithoutEchoingTheToken() throws Exception {
    when(tokenIssuerPort.isAccessTokenValid(REJECTED_TOKEN)).thenReturn(false);

    ResultActions result =
        mockMvc.perform(
            get("/api/v1/channels").header(HttpHeaders.AUTHORIZATION, "Bearer " + REJECTED_TOKEN));

    expectUnauthenticated(result);
    result.andExpect(content().string(not(containsString(REJECTED_TOKEN))));
    verifyNoInteractions(channelQuerier);
  }

  @Test
  void listChannels_acceptedToken_reachesTheUseCase() throws Exception {
    mockMvc
        .perform(get("/api/v1/channels").header(HttpHeaders.AUTHORIZATION, acceptedBearerToken()))
        .andExpect(status().isOk());

    verify(channelQuerier).getChannels(any());
  }

  @Test
  void logout_acceptedToken_reachesTheUseCaseWithTheCallerId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, acceptedBearerToken()))
        .andExpect(status().isNoContent());

    verify(tokenRevoker).logout(null, USER_ID);
  }

  // Login stays on the allowlist: its 401 comes from the use case, not from the entry point.
  @Test
  void login_noToken_reachesTheUseCase() throws Exception {
    when(authenticate.login(any())).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_401"));

    verify(authenticate).login(any());
  }

  // Refresh stays on the allowlist: the caller's access token may already be expired.
  @Test
  void refreshToken_noToken_reachesTheUseCase() throws Exception {
    mockMvc.perform(post("/api/v1/auth/refresh_token"));

    verify(tokenRefresher).refresh(null);
  }
}
