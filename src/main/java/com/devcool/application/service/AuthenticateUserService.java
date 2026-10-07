package com.devcool.application.service;

import com.devcool.domain.auth.exception.InvalidCredentialsException;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.port.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.port.in.command.LoginCommand;
import com.devcool.domain.auth.port.out.PasswordHasherPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import com.devcool.domain.auth.port.out.TokenHashPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticateUserService implements AuthenticateUserUseCase {
  private static final Logger log = LoggerFactory.getLogger(AuthenticateUserService.class);
  private final PasswordHasherPort passwordHasherPort;
  private final TokenIssuerPort tokenIssuerPort;
  private final RefreshTokenPort refreshTokenPort;
  private final TokenHashPort tokenHashPort;
  private final UserPort userPort;

  @Override
  @Transactional
  public TokenPair login(LoginCommand command) {
    Optional<User> maybeUser = userPort.findByUsername(command.username());
    if (maybeUser.isEmpty()) {
      log.warn("Login failed: no account for username '{}'", command.username());
      throw new InvalidCredentialsException();
    }
    User user = maybeUser.get();

    if (!passwordHasherPort.matches(command.password(), user.getPassword())) {
      log.warn("Login failed: incorrect password for username '{}'", command.username());
      throw new InvalidCredentialsException();
    }

    updateLoginTime(user);

    TokenPair tokenPair = tokenIssuerPort.issue(user);
    RefreshToken refreshToken =
        RefreshToken.issue(user.getId(), tokenHashPort.hash(tokenPair.refreshJti()), Instant.now());
    refreshTokenPort.deleteOldRefreshTokens(user.getId());
    refreshTokenPort.store(refreshToken);

    return tokenPair;
  }

  private void updateLoginTime(User user) {
    user.updateLoginTime();
    if (!userPort.updateLoginTime(user.getId(), user.getLastLoginTime())) {
      log.warn("Login time not recorded, user {} no longer exists", user.getId());
    }
  }
}
