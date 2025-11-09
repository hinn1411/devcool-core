package com.devcool.domain.auth.in;

public interface LogoutUseCase {
  void revokeRefreshToken(String refreshToken);

  void updateAccessTokenVersion(Integer userId);
}
