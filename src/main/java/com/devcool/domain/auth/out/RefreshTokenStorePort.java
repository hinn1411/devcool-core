package com.devcool.domain.auth.out;

import com.devcool.domain.auth.model.RefreshToken;

public interface RefreshTokenStorePort {
  void store(RefreshToken refreshToken);

  boolean consume(String jtiHash); // Automatically mark used/ revoke
}
