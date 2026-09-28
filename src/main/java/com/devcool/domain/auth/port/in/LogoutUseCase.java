package com.devcool.domain.auth.port.in;

public interface LogoutUseCase {
  void logout(String refreshToken, Integer userId);
}
