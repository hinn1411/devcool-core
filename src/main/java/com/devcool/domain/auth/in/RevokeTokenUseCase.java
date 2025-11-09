package com.devcool.domain.auth.in;

public interface RevokeTokenUseCase {
  void revoke(String refreshToken);

  void updateAccessTokenVersion(Integer userId);
}
