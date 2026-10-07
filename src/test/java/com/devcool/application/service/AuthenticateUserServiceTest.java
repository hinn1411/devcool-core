package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.auth.exception.InvalidCredentialsException;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.port.in.command.LoginCommand;
import com.devcool.domain.auth.port.out.PasswordHasherPort;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private RefreshTokenPort refreshTokenPort;
  @Mock private TokenHashPort tokenHashPort;
  @Mock private UserPort userPort;

  @InjectMocks private AuthenticateUserService service;

  @Test
  void login_unknownUsername_throwsInvalidCredentials() {
    when(userPort.findByUsername("alice")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginCommand("alice", "secret")))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(tokenIssuerPort, refreshTokenPort);
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    User user = mock(User.class);
    when(user.getPassword()).thenReturn("hashed");
    when(userPort.findByUsername("bob")).thenReturn(Optional.of(user));
    when(passwordHasherPort.matches("wrong", "hashed")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginCommand("bob", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(tokenIssuerPort, refreshTokenPort);
  }

  @Test
  void login_validCredentials_storesTheRefreshTokenHashedForSevenDays() {
    User user = User.builder().id(7).password("hashed").build();
    TokenPair pair = new TokenPair("access", "refresh", "refresh-jti");
    when(userPort.findByUsername("carol")).thenReturn(Optional.of(user));
    when(passwordHasherPort.matches("secret", "hashed")).thenReturn(true);
    when(userPort.updateLoginTime(eq(7), any())).thenReturn(true);
    when(tokenIssuerPort.issue(user)).thenReturn(pair);
    when(tokenHashPort.hash("refresh-jti")).thenReturn("hash-of-refresh-jti");

    assertThat(service.login(new LoginCommand("carol", "secret"))).isEqualTo(pair);

    ArgumentCaptor<RefreshToken> stored = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenPort).deleteOldRefreshTokens(7);
    verify(refreshTokenPort).store(stored.capture());
    assertThat(stored.getValue().getJti()).isEqualTo("hash-of-refresh-jti");
    assertThat(stored.getValue().getUserId()).isEqualTo(7);
    assertThat(
            Duration.between(stored.getValue().getIssuedTime(), stored.getValue().getExpiredTime()))
        .isEqualTo(Duration.ofDays(7));
  }
}
