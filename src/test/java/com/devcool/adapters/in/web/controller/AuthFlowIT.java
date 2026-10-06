package com.devcool.adapters.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devcool.adapters.in.web.dto.request.ChangePasswordRequest;
import com.devcool.adapters.in.web.dto.request.LoginRequest;
import com.devcool.adapters.in.web.dto.request.RegisterUserRequest;
import com.devcool.domain.common.ErrorCode;
import com.devcool.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The refresh-cookie round trip (exercise 8, P1-T12 part 3): login, refresh, replay, logout and
 * password change, through the real filter chain. Not @Transactional: every request commits, as in
 * production, and the tables are truncated before each test instead.
 *
 * <p>MockMvc keeps no cookie jar, so the {@code rt} value is carried by hand, and the attributes a
 * browser would enforce are asserted on the raw {@code Set-Cookie} header.
 */
class AuthFlowIT extends AbstractIntegrationTest {

  private static final String AUTH = "/api/v1/auth";
  private static final String RT = "rt";
  private static final String PASSWORD = "Secret#123";
  private static final String NEW_PASSWORD = "Changed#456";
  private static final long SEVEN_DAYS_SECONDS = 7L * 24 * 60 * 60;

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  record Account(Integer id, String username) {}

  /** What a client holds after login or refresh: the bearer token and the {@code rt} value. */
  record Session(String accessToken, String refreshCookie) {}

  /** The user's refresh_token rows: all of them, and how many are consumed. */
  record RefreshTokenRows(int total, int consumed) {}

  /** Everything a rejected request must leave untouched. */
  record AuthState(int tokenVersion, RefreshTokenRows refreshTokens) {}

  /** Responses that must hand the browser a live {@code rt}. */
  enum CookieIssuer {
    LOGIN,
    REFRESH
  }

  /** Responses that must make the browser drop {@code rt}. */
  enum CookieClearer {
    LOGOUT,
    CHANGE_PASSWORD
  }

  /** What the client sends as {@code rt} on a refresh that must be refused. */
  enum RejectedCookie {
    MISSING, // no cookie at all
    REPLAYED, // already exchanged once
    MALFORMED, // not a JWT
    ACCESS_TOKEN // a valid JWT, but signed with the access key and typed ACCESS
  }

  /** A credential the client held before logout or a password change, and how it's refused. */
  enum StaleCredential {
    ACCESS_TOKEN(ErrorCode.UNAUTHENTICATED), // GET /profile, answered by the entry point
    REFRESH_COOKIE(ErrorCode.REFRESH_TOKEN_INVALID); // POST /refresh_token

    final ErrorCode rejection;

    StaleCredential(ErrorCode rejection) {
      this.rejection = rejection;
    }
  }

  @BeforeEach
  void cleanDatabase() {
    // Before, not after: a test that crashed halfway still leaves the next one a clean DB
    jdbc.execute(
        """
        TRUNCATE refresh_token, media, message, member, topics_of_channels,
                 channel, friend_request, auth_provider, topic, app_user
        CASCADE""");
  }

  @Nested
  class Login {

    // The reference test: arrange / act / assert, with the helpers below doing the plumbing.
    @Test
    void returnsAccessTokenInBodyAndRefreshTokenInCookie() throws Exception {
      Account account = register();

      ResultActions result = login(account);

      expectApiResponse(result, 200, ErrorCode.OK.code());
      Session session = sessionFrom(result);
      assertThat(session.accessToken()).isNotBlank();
      assertThat(session.refreshCookie()).isNotBlank();
      // The token is real: the filter chain accepts it
      assertAccessTokenAccepted(session.accessToken());
      // One refresh token stored, not yet used
      assertThat(refreshTokens(account)).isEqualTo(new RefreshTokenRows(1, 0));
    }
  }

  @Nested
  class RefreshCookieAttributes {

    @ParameterizedTest(name = "{0} sets a live rt cookie")
    @EnumSource(CookieIssuer.class)
    void issuedCookieCarriesBrowserAttributes(CookieIssuer issuer) throws Exception {
      Account account = register();

      ResultActions result =
          switch (issuer) {
            case LOGIN -> login(account);
            case REFRESH -> refresh(loginSession(account).refreshCookie());
          };

      String rawCookie = rtSetCookie(result);
      assertLiveRefreshCookie(rawCookie);
    }

    @ParameterizedTest(name = "{0} expires the rt cookie")
    @EnumSource(CookieClearer.class)
    void clearedCookieExpiresRt(CookieClearer clearer) throws Exception {
      Session session = loginSession(register());

      ResultActions result =
          switch (clearer) {
            case LOGOUT -> logout(session);
            case CHANGE_PASSWORD -> changePassword(session);
          };

      expectNoContent(result);
      String rawCookie = rtSetCookie(result);
      assertExpiredRefreshCookie(rawCookie);
    }
  }

  @Nested
  class RefreshToken {

    @Test
    void validCookie_returnsNewAccessTokenAndRotatedCookie() throws Exception {
      Account account = register();
      Session login = loginSession(account);
      AuthState before = authState(account);

      ResultActions result = refresh(login.refreshCookie());

      expectApiResponse(result, 200, ErrorCode.OK.code());
      Session refreshed = sessionFrom(result);
      assertThat(refreshed.accessToken()).isNotEqualTo(login.accessToken());
      assertThat(refreshed.refreshCookie()).isNotEqualTo(login.refreshCookie());
      assertAccessTokenAccepted(refreshed.accessToken());
      assertThat(refreshTokens(account)).isEqualTo(new RefreshTokenRows(2, 1));
      assertThat(tokenVersion(account)).isEqualTo(before.tokenVersion());
    }

    @ParameterizedTest(name = "{0} -> 401")
    @EnumSource(RejectedCookie.class)
    void rejectedCookie_isUnauthorizedAndChangesNothing(RejectedCookie kind) throws Exception {
      Account account = register();
      String cookie = arrangeRejectedCookie(kind, account);
      AuthState before = authState(account);

      ResultActions result = refresh(cookie);

      expectApiResponse(result, 401, ErrorCode.REFRESH_TOKEN_INVALID.code());
      assertThat(rtSetCookies(result)).isEmpty();
      assertAuthStateUnchanged(account, before);
    }
  }

  @Nested
  class Logout {

    @Test
    void withAccessTokenAndCookie_revokesTheSession() throws Exception {
      Account account = register();
      Session session = loginSession(account);
      AuthState before = authState(account);

      ResultActions result = logout(session);

      // Cookie attributes are covered by RefreshCookieAttributes.clearedCookieExpiresRt
      expectNoContent(result);
      assertThat(tokenVersion(account)).isGreaterThan(before.tokenVersion());
      assertThat(refreshTokens(account)).isEqualTo(new RefreshTokenRows(1, 1));
    }

    @ParameterizedTest(name = "old {0} is rejected after logout")
    @EnumSource(StaleCredential.class)
    void oldCredential_isRejected(StaleCredential credential) throws Exception {
      Session session = loginSession(register());
      expectNoContent(logout(session));

      ResultActions result = useStale(credential, session);

      expectRejected(credential, result);
    }
  }

  @Nested
  class ChangePassword {

    @Test
    void validRequest_revokesEverySession() throws Exception {
      Account account = register();
      // Two logins, so "every refresh token is gone" covers more than the caller's own
      loginSession(account);
      Session caller = loginSession(account);
      AuthState before = authState(account);

      ResultActions result = changePassword(caller);

      expectNoContent(result);
      assertThat(refreshTokens(account)).isEqualTo(new RefreshTokenRows(0, 0));
      assertThat(tokenVersion(account)).isGreaterThan(before.tokenVersion());
    }

    @ParameterizedTest(name = "old {0} is rejected after a password change")
    @EnumSource(StaleCredential.class)
    void oldCredential_isRejected(StaleCredential credential) throws Exception {
      Account account = register();
      Session other = loginSession(account);
      Session caller = loginSession(account);
      expectNoContent(changePassword(caller));

      // The caller's own credential, then another device's: a password change logs out everywhere
      expectRejected(credential, useStale(credential, caller));
      expectRejected(credential, useStale(credential, other));
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Act: one method per endpoint. Each returns the raw ResultActions, so the test asserts.
  // ---------------------------------------------------------------------------------------------

  private ResultActions login(Account account) throws Exception {
    return mvc.perform(
        post(AUTH + "/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new LoginRequest(account.username(), PASSWORD))));
  }

  /** {@code cookie == null} sends no {@code rt} cookie at all. */
  private ResultActions refresh(String cookie) throws Exception {
    MockHttpServletRequestBuilder request = post(AUTH + "/refresh_token");
    return mvc.perform(cookie == null ? request : request.cookie(new Cookie(RT, cookie)));
  }

  private ResultActions logout(Session session) throws Exception {
    return mvc.perform(
        post(AUTH + "/logout")
            .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
            .cookie(new Cookie(RT, session.refreshCookie())));
  }

  /** PASSWORD → NEW_PASSWORD, a request the service accepts. */
  private ResultActions changePassword(Session session) throws Exception {
    var request = new ChangePasswordRequest(PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
    return mvc.perform(
        post(AUTH + "/password")
            .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)));
  }

  private ResultActions profile(String accessToken) throws Exception {
    return mvc.perform(
        get(AUTH + "/profile").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
  }

  /** Uses each credential where it is accepted: the access token on /profile, rt on refresh. */
  private ResultActions useStale(StaleCredential credential, Session session) throws Exception {
    return switch (credential) {
      case ACCESS_TOKEN -> profile(session.accessToken());
      case REFRESH_COOKIE -> refresh(session.refreshCookie());
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Arrange: preconditions are checked here, so a broken setup fails loudly before the act.
  // ---------------------------------------------------------------------------------------------

  /** A fresh user per test, registered through the API. */
  private Account register() throws Exception {
    // user_name is varchar(20): "af_" + 10 chars
    String username = "af_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    var request = new RegisterUserRequest(username, PASSWORD, username + "@test.dev", "Auth Flow");
    ResultActions registered =
        mvc.perform(
                post(AUTH + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated());
    Integer id = JsonPath.read(body(registered), "$.data.userId");
    return new Account(id, username);
  }

  /** A successful login, as the client would keep it. */
  private Session loginSession(Account account) throws Exception {
    return sessionFrom(login(account).andExpect(status().isOk()));
  }

  /** Reads a login or refresh response: the body's access token and the rt cookie's value. */
  private static Session sessionFrom(ResultActions result) throws Exception {
    String accessToken = JsonPath.read(body(result), "$.data.accessToken");
    return new Session(accessToken, cookieValue(rtSetCookie(result)));
  }

  private String arrangeRejectedCookie(RejectedCookie kind, Account account) throws Exception {
    return switch (kind) {
      case MISSING -> null;
      case MALFORMED -> "not-a-jwt";
      case ACCESS_TOKEN -> loginSession(account).accessToken();
      case REPLAYED -> {
        Session session = loginSession(account);
        // The first exchange must succeed, or the replay below proves nothing
        refresh(session.refreshCookie()).andExpect(status().isOk());
        yield session.refreshCookie();
      }
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Assert: reusable checks on responses, cookies and rows.
  // ---------------------------------------------------------------------------------------------

  /** HTTP status, {@code $.status} and {@code $.code} all agree. */
  private static void expectApiResponse(ResultActions result, int status, String code)
      throws Exception {
    result
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.code").value(code));
  }

  /** 401 with the code the credential's own endpoint answers with. */
  private static void expectRejected(StaleCredential credential, ResultActions result)
      throws Exception {
    expectApiResponse(result, 401, credential.rejection.code());
  }

  private static void expectNoContent(ResultActions result) throws Exception {
    result.andExpect(status().isNoContent());
  }

  /** The attributes a browser enforces, compared whole: "Path=/a" must not match "Path=/a/b". */
  private static void assertLiveRefreshCookie(String setCookie) {
    assertThat(cookieValue(setCookie)).as("rt value").isNotBlank();
    assertThat(cookieAttributes(setCookie))
        .contains(
            "Path=" + AUTH,
            "HttpOnly",
            "Secure",
            "SameSite=Strict",
            "Max-Age=" + SEVEN_DAYS_SECONDS);
  }

  /** Expires rt on the same path it was set on, or the browser keeps the old one. */
  private static void assertExpiredRefreshCookie(String setCookie) {
    assertThat(cookieValue(setCookie)).as("rt value").isEmpty();
    assertThat(cookieAttributes(setCookie)).contains("Path=" + AUTH, "Max-Age=0");
  }

  private void assertAccessTokenAccepted(String accessToken) throws Exception {
    profile(accessToken).andExpect(status().isOk());
  }

  private void assertAuthStateUnchanged(Account account, AuthState before) {
    assertThat(authState(account)).isEqualTo(before);
  }

  // ---------------------------------------------------------------------------------------------
  // Cookie parsing and DB reads
  // ---------------------------------------------------------------------------------------------

  /** Every raw Set-Cookie header for rt. Empty when the response set none. */
  private static List<String> rtSetCookies(ResultActions result) {
    return result.andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(header -> header.startsWith(RT + "="))
        .toList();
  }

  /** The single raw Set-Cookie header for rt; fails if there is none or more than one. */
  private static String rtSetCookie(ResultActions result) {
    List<String> headers = rtSetCookies(result);
    assertThat(headers).as("Set-Cookie headers for " + RT).hasSize(1);
    return headers.get(0);
  }

  /** "rt=abc; Path=/x; HttpOnly" → "abc". */
  private static String cookieValue(String setCookie) {
    String pair = setCookie.split(";", 2)[0];
    return pair.substring(pair.indexOf('=') + 1);
  }

  /** "rt=abc; Path=/x; HttpOnly" → ["Path=/x", "HttpOnly"]. */
  private static List<String> cookieAttributes(String setCookie) {
    return Arrays.stream(setCookie.split(";")).skip(1).map(String::trim).toList();
  }

  private int tokenVersion(Account account) {
    return jdbc.queryForObject(
        "select token_version from app_user where id = ?", Integer.class, account.id());
  }

  /** refresh_token.user_id is a varchar, hence the String id. */
  private RefreshTokenRows refreshTokens(Account account) {
    return jdbc.queryForObject(
        "select count(*) as total, count(consumed_time) as consumed"
            + " from refresh_token where user_id = ?",
        (rs, rowNum) -> new RefreshTokenRows(rs.getInt("total"), rs.getInt("consumed")),
        String.valueOf(account.id()));
  }

  private AuthState authState(Account account) {
    return new AuthState(tokenVersion(account), refreshTokens(account));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private static String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }
}
