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

import com.devcool.domain.auth.exception.RefreshTokenInvalidException;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import com.devcool.domain.auth.port.out.TokenHashPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  private static final String JTI_HASH = "hash-of-jti-1";

  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private UserPort userPort;
  @Mock private TokenHashPort tokenHashPort;
  @Mock private RefreshTokenPort refreshTokenPort;
  @Mock private AccessTokenPort accessTokenPort;

  @InjectMocks private RefreshTokenService service;

  @Test
  void refresh_validToken_consumesItAndStoresTheRotatedOneHashedForSevenDays() {
    User user = User.builder().id(USER_ID).build();
    TokenPair rotated = new TokenPair("new-access-token", "new-refresh-token", "jti-2");
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.of(SUBJECT));
    when(tokenHashPort.hash(JTI)).thenReturn(JTI_HASH);
    when(tokenHashPort.hash("jti-2")).thenReturn("hash-of-jti-2");
    when(refreshTokenPort.consumeIfValid(JTI_HASH)).thenReturn(true);
    when(userPort.findById(USER_ID)).thenReturn(Optional.of(user));
    when(tokenIssuerPort.rotate(user, JTI)).thenReturn(rotated);

    assertThat(service.refresh(REFRESH_TOKEN)).isEqualTo(rotated);

    ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenPort).store(stored.capture());
    assertThat(stored.getValue().getJti()).isEqualTo("hash-of-jti-2");
    assertThat(stored.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(
            Duration.between(stored.getValue().getIssuedTime(), stored.getValue().getExpiredTime()))
        .isEqualTo(Duration.ofDays(7));
  }

  @Test
  void refresh_tokenRejectedByTheIssuer_throwsAndWritesNothing() {
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN))
        .isInstanceOf(RefreshTokenInvalidException.class)
        .hasMessageNotContaining(REFRESH_TOKEN);

    verifyNoInteractions(refreshTokenPort, userPort, accessTokenPort);
  }

  @Test
  void refresh_missingToken_throwsAndWritesNothing() {
    assertThatThrownBy(() -> service.refresh(null))
        .isInstanceOf(RefreshTokenInvalidException.class);

    verifyNoInteractions(refreshTokenPort, userPort, accessTokenPort);
  }

  @Test
  void refresh_tokenAlreadyConsumed_throwsAndIssuesNothing() {
    when(tokenIssuerPort.verifyRefresh(REFRESH_TOKEN)).thenReturn(Optional.of(SUBJECT));
    when(tokenHashPort.hash(JTI)).thenReturn(JTI_HASH);
    when(refreshTokenPort.consumeIfValid(JTI_HASH)).thenReturn(false);

    assertThatThrownBy(() -> service.refresh(REFRESH_TOKEN))
        .isInstanceOf(RefreshTokenInvalidException.class);

    verify(tokenIssuerPort, never()).rotate(any(), any());
    verify(refreshTokenPort, never()).store(any());
    verifyNoInteractions(userPort);
  }

  @Test
  void logout_revokesRefreshTokenThenBumpsAccessTokenVersion() {
    String hashJti = JTI_HASH;
    when(tokenHashPort.hash(JTI)).thenReturn(JTI_HASH);
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
    String hashJti = JTI_HASH;
    when(tokenHashPort.hash(JTI)).thenReturn(JTI_HASH);
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
