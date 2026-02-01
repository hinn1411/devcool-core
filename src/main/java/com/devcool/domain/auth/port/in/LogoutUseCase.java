package com.devcool.domain.auth.port.in;

public interface LogoutUseCase {
  void revokeRefreshToken(String refreshToken);

  void updateAccessTokenVersion(Integer userId);
}
