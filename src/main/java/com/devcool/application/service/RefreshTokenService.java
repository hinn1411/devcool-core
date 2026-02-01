package com.devcool.application.service;

import com.devcool.adapters.out.crypto.util.HashUtils;
import com.devcool.adapters.out.jwt.util.JwtUtils;
import com.devcool.domain.auth.exception.RefreshTokenInvalidException;
import com.devcool.domain.auth.port.in.LogoutUseCase;
import com.devcool.domain.auth.port.in.RefreshTokenUseCase;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.auth.port.out.AccessTokenPort;
import com.devcool.domain.auth.port.out.LoadUserPort;
import com.devcool.domain.auth.port.out.RefreshTokenStorePort;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.exception.UserNotFoundException;
import com.devcool.domain.user.model.User;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase, LogoutUseCase {
  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private final TokenIssuerPort issuer;
  private final LoadUserPort loadUser;
  private final RefreshTokenStorePort refreshStore;
  private final AccessTokenPort accessTokenPort;

  @Override
  public TokenPair refresh(String rawRefreshToken) {
    TokenSubject sub = issuer.verifyRefresh(rawRefreshToken);
    String jtiHash = HashUtils.sha256(sub.jti());

    if (!refreshStore.consumeIfValid(jtiHash)) {
      throw new CredentialsExpiredException("Refresh token invalid, expired or already in used");
    }

    User user =
        loadUser
            .loadById(Integer.valueOf(sub.userId()))
            .orElseThrow(
                () -> {
                  log.warn("User: {} not found!", sub.userId());
                  return new UserNotFoundException(Integer.valueOf(sub.userId()));
                });

    TokenPair tokenPair = issuer.rotate(user, sub.jti());
    RefreshToken refreshToken = JwtUtils.buildRefreshToken(user, tokenPair);
    refreshStore.store(refreshToken);

    return tokenPair;
  }

  @Override
  public void revokeRefreshToken(String refreshToken) {
    if (Objects.isNull(refreshToken)) {
      throw new RefreshTokenInvalidException("Empty refresh token");
    }

    String jti = JwtUtils.jtiFrom(refreshToken);
    String hashJti = HashUtils.sha256(jti);
    if (!refreshStore.revoke(hashJti)) {
      log.warn("Cannot revoke token!");
    }
  }

  @Override
  public void updateAccessTokenVersion(Integer userId) {
    if (Objects.isNull(userId)) {
      throw new UserNotFoundException(userId);
    }
    if (!accessTokenPort.updateVersion(userId)) {
      log.warn("Cannot update access token version");
    }
  }
}
