package com.devcool.domain.auth.port.in;

import com.devcool.domain.auth.model.TokenPair;

public interface RefreshTokenUseCase {
  TokenPair refresh(String refreshToken);
}
