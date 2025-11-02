package com.devcool.application.service;

import com.devcool.adapters.out.crypto.util.HashUtils;
import com.devcool.adapters.out.jwt.util.JwtUtils;
import com.devcool.domain.auth.exception.PasswordIncorrectException;
import com.devcool.domain.auth.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.in.command.LoginCommand;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.out.LoadUserPort;
import com.devcool.domain.auth.out.PasswordHasherPort;
import com.devcool.domain.auth.out.RefreshTokenStorePort;
import com.devcool.domain.auth.out.TokenIssuerPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticateUserService implements AuthenticateUserUseCase {
  private static final Logger log = LoggerFactory.getLogger(AuthenticateUserService.class);
  private final LoadUserPort loadUser;
  private final PasswordHasherPort hasher;
  private final TokenIssuerPort issuer;
  private final RefreshTokenStorePort refreshStore;
  private final UserPort userPort;

  @Override
  @Transactional
  public TokenPair login(LoginCommand command) {
    User user =
        loadUser
            .loadByUsername(command.username())
            .orElseThrow(() -> new UserNotFoundException(-1));

    if (!hasher.matches(command.password(), user.getPassword())) {
      log.warn("Password does not match!");
      throw new PasswordIncorrectException(command.password());
    }

    updateLoginTime(user);

    TokenPair tokenPair = issuer.issue(user);
    RefreshToken refreshToken = buildRefreshToken(user, tokenPair);
    refreshStore.store(refreshToken);
    return tokenPair;
  }

  private void updateLoginTime(User user) {
    user.updateLoginTime();
    userPort.save(user);
  }

  private String hashJtiFrom(String refreshToken) {
    return HashUtils.sha256(JwtUtils.jtiFrom(refreshToken));
  }

  private RefreshToken buildRefreshToken(User user, TokenPair tokenPair) {
    return RefreshToken.builder()
        .jti(hashJtiFrom(tokenPair.refreshToken()))
        .userId(user.getId())
        .issuedTime(Instant.now())
        .expiredTime(Instant.now().plus(7, ChronoUnit.DAYS))
        .consumedTime(null)
        .build();
  }
}
