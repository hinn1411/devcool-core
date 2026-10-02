package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.adapters.out.crypto.util.HashUtils;
import com.devcool.domain.auth.exception.RefreshTokenInvalidException;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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

  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private LoadUserPort loadUserPort;
  @Mock private RefreshTokenPort refreshTokenPort;
  @Mock private AccessTokenPort accessTokenPort;

  @InjectMocks private RefreshTokenService service;

  // JwtUtils.jtiFrom parses the token but does not verify the signature, so any
  // correctly-shaped signed JWT carrying a jti will do.
  private static String refreshTokenWithJti(String jti) throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("7").jwtID(jti).build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));
    return jwt.serialize();
  }

  @Test
  void logout_revokesRefreshTokenThenBumpsAccessTokenVersion() throws JOSEException {
    String hashJti = HashUtils.sha256(JTI);
    when(refreshTokenPort.revoke(hashJti)).thenReturn(true);
    when(accessTokenPort.updateVersion(USER_ID)).thenReturn(true);

    service.logout(refreshTokenWithJti(JTI), USER_ID);

    InOrder inOrder = inOrder(refreshTokenPort, accessTokenPort);
    inOrder.verify(refreshTokenPort).revoke(hashJti);
    inOrder.verify(accessTokenPort).updateVersion(USER_ID);
  }

  @Test
  void logout_refreshTokenAlreadyRevoked_stillBumpsAccessTokenVersion() throws JOSEException {
    String hashJti = HashUtils.sha256(JTI);
    when(refreshTokenPort.revoke(hashJti)).thenReturn(false);
    when(accessTokenPort.updateVersion(USER_ID)).thenReturn(true);

    assertThatCode(() -> service.logout(refreshTokenWithJti(JTI), USER_ID))
        .doesNotThrowAnyException();

    verify(accessTokenPort).updateVersion(USER_ID);
  }

  @Test
  void logout_nullRefreshToken_throwsAndWritesNothing() {
    assertThatThrownBy(() -> service.logout(null, USER_ID))
        .isInstanceOf(RefreshTokenInvalidException.class);

    verifyNoInteractions(refreshTokenPort, accessTokenPort);
  }

  @Test
  void logout_nullUserId_failsFastAndWritesNothing() throws JOSEException {
    String token = refreshTokenWithJti(JTI);

    assertThatThrownBy(() -> service.logout(token, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId");

    verifyNoInteractions(refreshTokenPort, accessTokenPort);
  }
}
