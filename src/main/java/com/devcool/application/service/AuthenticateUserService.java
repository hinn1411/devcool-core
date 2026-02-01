package com.devcool.application.service;

import com.devcool.adapters.out.jwt.util.JwtUtils;
import com.devcool.domain.auth.exception.PasswordIncorrectException;
import com.devcool.domain.auth.port.in.AuthenticateUserUseCase;
import com.devcool.domain.auth.port.in.command.LoginCommand;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.PasswordHasherPort;
import com.devcool.domain.auth.port.out.RefreshTokenStorePort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
  public TokenPair login(LoginCommand command) {
    User user =
        loadUser
            .loadByUsername(command.username())
            .orElseThrow(() -> new UserNotFoundException(command.username()));

    if (!hasher.matches(command.password(), user.getPassword())) {
      log.warn("Password does not match!");
      throw new PasswordIncorrectException(command.password());
    }

    updateLoginTime(user);

    TokenPair tokenPair = issuer.issue(user);
    RefreshToken refreshToken = JwtUtils.buildRefreshToken(user, tokenPair);
    refreshStore.deleteOldRefreshTokens(user.getId());
    refreshStore.store(refreshToken);

    return tokenPair;
  }

  private void updateLoginTime(User user) {
    user.updateLoginTime();
    userPort.save(user);
  }
}
