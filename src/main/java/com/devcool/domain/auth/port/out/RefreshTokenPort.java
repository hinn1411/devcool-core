package com.devcool.domain.auth.port.out;

import com.devcool.domain.auth.model.RefreshToken;

public interface RefreshTokenPort {
  void store(RefreshToken refreshToken);

  boolean consumeIfValid(String jtiHash);

  void deleteOldRefreshTokens(Long userId);

  boolean revoke(String jtiHash);
}
