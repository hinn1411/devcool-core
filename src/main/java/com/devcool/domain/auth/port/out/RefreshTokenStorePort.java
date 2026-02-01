package com.devcool.domain.auth.port.out;

import com.devcool.domain.auth.model.RefreshToken;

public interface RefreshTokenStorePort {
  void store(RefreshToken refreshToken);

  boolean consumeIfValid(String jtiHash);

  void deleteOldRefreshTokens(Integer userId);

  boolean revoke(String jtiHash);
}
