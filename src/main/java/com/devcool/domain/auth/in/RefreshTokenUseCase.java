package com.devcool.domain.auth.in;

import com.devcool.domain.auth.model.TokenPair;

public interface RefreshTokenUseCase {
    TokenPair refresh(String refreshToken);
}
