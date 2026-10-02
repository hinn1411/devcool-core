package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.adapters.out.crypto.util.HashUtils;
import com.devcool.domain.auth.exception.RefreshTokenInvalidException;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final String JTI = "jti-1";
  private static final Integer USER_ID = 7;
  private static final String REFRESH_TOKEN = "presented-refresh-token";
  private static final TokenSubject SUBJECT = new TokenSubject("7", JTI);

  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private LoadUserPort loadUserPort;
  @Mock private RefreshTokenPort refreshTokenPort;
  @Mock private AccessTokenPort accessTokenPort;

  @InjectMocks private RefreshTokenService service;

  // JwtUtils.buildRefreshToken reads the jti of the newly issued token, so the rotated pair
  // needs a correctly-shaped JWT.
  private static String refreshTokenWithJti(String jti) throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("7").jwtID(jti).build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
    return jwt.serialize();
  }

  @Test
  void refresh_validToken_consumesItAndStoresTheRotatedOne() throws JOSEException {
    User user = User.builder().id(USER_ID).build();
    TokenPair rotated = new TokenPair("new-access-token", refreshTokenWithJti("jti-2"));
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.of(SUBJECT));
    when(refreshTokenPort.consumeIfValid(HashUtils.sha256(JTI))).thenReturn(true);
    when(loadUserPort.loadById(USER_ID)).thenReturn(Optional.of(user));
    when(tokenIssuerPort.rotate(user, JTI)).thenReturn(rotated);

    assertThat(service.refresh(REFRESH_TOKEN)).isEqualTo(rotated);

    verify(refreshTokenPort).store(any());
  }

  @Test
  void refresh_tokenRejectedByTheIssuer_throwsAndWritesNothing() {
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN))
        .isInstanceOf(RefreshTokenInvalidException.class)
        .hasMessageNotContaining(REFRESH_TOKEN);

    verifyNoInteractions(refreshTokenPort, loadUserPort, accessTokenPort);
  }

  @Test
  void refresh_missingToken_throwsAndWritesNothing() {
    assertThatThrownBy(() -> service.refresh(null))
        .isInstanceOf(RefreshTokenInvalidException.class);

    verifyNoInteractions(refreshTokenPort, loadUserPort, accessTokenPort);
  }

  @Test
  void refresh_tokenAlreadyConsumed_throwsAndIssuesNothing() {
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.of(SUBJECT));
    when(refreshTokenPort.consumeIfValid(HashUtils.sha256(JTI))).thenReturn(false);

    assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN))
        .isInstanceOf(RefreshTokenInvalidException.class);

    verify(tokenIssuerPort, never()).rotate(any(), any());
    verify(refreshTokenPort, never()).store(any());
    verifyNoInteractions(loadUserPort);
  }

  @Test
  void logout_revokesRefreshTokenThenBumpsAccessTokenVersion() {
    String hashJti = HashUtils.sha256(JTI);
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.of(SUBJECT));
    when(refreshTokenPort.revoke(hashJti)).thenReturn(true);
    when(accessTokenPort.updateVersion(USER_ID)).thenReturn(true);

    service.logout(REFRESH_TOKEN, USER_ID);

    InOrder inOrder = inOrder(refreshTokenPort, accessTokenPort);
    inOrder.verify(refreshTokenPort).revoke(hashJti);
    inOrder.verify(accessTokenPort).updateVersion(USER_ID);
  }

  @Test
  void logout_refreshTokenAlreadyRevoked_stillBumpsAccessTokenVersion() {
    String hashJti = HashUtils.sha256(JTI);
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.of(SUBJECT));
    when(refreshTokenPort.revoke(hashJti)).thenReturn(false);
    when(accessTokenPort.updateVersion(USER_ID)).thenReturn(true);

    assertThatCode(() -> service.logout(REFRESH_TOKEN, USER_ID)).doesNotThrowAnyException();

    verify(accessTokenPort).updateVersion(USER_ID);
  }

  @Test
  void logout_tokenRejectedByTheIssuer_throwsAndWritesNothing() {
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.logout(REFRESH_TOKEN, USER_ID))
        .isInstanceOf(RefreshTokenInvalidException.class)
        .hasMessageNotContaining(REFRESH_TOKEN);

    verifyNoInteractions(refreshTokenPort, accessTokenPort);
  }

  @Test
  void logout_nullRefreshToken_throwsAndWritesNothing() {
    assertThatThrownBy(() -> service.logout(null, USER_ID))
        .isInstanceOf(RefreshTokenInvalidException.class);

    verifyNoInteractions(refreshTokenPort, accessTokenPort);
  }

  @Test
  void logout_nullUserId_failsFastAndWritesNothing() {
    assertThatThrownBy(() -> service.logout(REFRESH_TOKEN, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId");

    verifyNoInteractions(tokenIssuerPort, refreshTokenPort, accessTokenPort);
  }
}
