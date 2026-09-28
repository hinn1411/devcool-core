package com.devcool.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devcool.domain.auth.exception.InvalidCredentialsException;
import com.devcool.domain.auth.port.in.command.LoginCommand;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.PasswordHasherPort;
import com.devcool.domain.auth.port.out.RefreshTokenStorePort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

  @Mock private LoadUserPort loadUser;
  @Mock private PasswordHasherPort hasher;
  @Mock private TokenIssuerPort issuer;
  @Mock private RefreshTokenStorePort refreshStore;
  @Mock private UserPort userPort;

  @InjectMocks private AuthenticateUserService service;

  @Test
  void login_unknownUsername_throwsInvalidCredentials() {
    when(loadUser.loadByUsername("alice")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginCommand("alice", "secret")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_wrongPassword_throwsInvalidCredentials() {
    User user = mock(User.class);
    when(user.getPassword()).thenReturn("hashed");
    when(loadUser.loadByUsername("bob")).thenReturn(Optional.of(user));
    when(hasher.matches("wrong", "hashed")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginCommand("bob", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
