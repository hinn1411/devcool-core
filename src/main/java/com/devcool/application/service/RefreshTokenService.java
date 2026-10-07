package com.devcool.application.service;

import com.devcool.domain.auth.exception.RefreshTokenInvalidException;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.auth.port.in.LogoutUseCase;
import com.devcool.domain.auth.port.in.RefreshTokenUseCase;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.RefreshTokenPort;
import com.devcool.domain.auth.port.out.TokenHashPort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.port.out.UserPort;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase, LogoutUseCase {
  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private final TokenIssuerPort tokenIssuerPort;
  private final UserPort userPort;
  private final TokenHashPort tokenHashPort;
  private final RefreshTokenPort refreshTokenPort;
  private final AccessTokenPort accessTokenPort;

  @Override
  @Transactional
  public TokenPair refresh(String rawRefreshToken) {
    TokenSubject sub = requireValid(rawRefreshToken);
    String jtiHash = tokenHashPort.hash(sub.jti());

    if (!refreshTokenPort.consumeIfValid(jtiHash)) {
      throw new RefreshTokenInvalidException("Refresh token expired or already used");
    }

    User user =
        userPort
            .findById(Integer.valueOf(sub.userId()))
            .orElseThrow(
                () -> {
                  log.warn("User: {} not found!", sub.userId());
                  return new UserNotFoundException(Integer.valueOf(sub.userId()));
                });

    TokenPair tokenPair = tokenIssuerPort.rotate(user, sub.jti());
    RefreshToken refreshToken =
        RefreshToken.issue(user.getId(), tokenHashPort.hash(tokenPair.refreshJti()), Instant.now());
    refreshTokenPort.store(refreshToken);

    return tokenPair;
  }

  @Override
  @Transactional
  public void logout(String refreshToken, Integer userId) {
    Objects.requireNonNull(userId, "userId must not be null");
    TokenSubject sub = requireValid(refreshToken);

    String hashJti = tokenHashPort.hash(sub.jti());
    if (!refreshTokenPort.revoke(hashJti)) {
      log.warn("Cannot revoke token!");
    }
    if (!accessTokenPort.updateVersion(userId)) {
      log.warn("Cannot update access token version");
    }
  }

  private TokenSubject requireValid(String rawRefreshToken) {
    if (Objects.isNull(rawRefreshToken)) {
      throw new RefreshTokenInvalidException("Refresh token is null");
    }
    TokenSubject tokenSubject =
        tokenIssuerPort
            .verifyRefresh(rawRefreshToken)
            .orElseThrow(() -> new RefreshTokenInvalidException("Refresh token invalid"));
    return tokenSubject;
  }
}
