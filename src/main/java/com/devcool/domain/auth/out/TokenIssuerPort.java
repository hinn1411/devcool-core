package com.devcool.domain.auth.out;

import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.user.model.User;

public interface TokenIssuerPort {
  TokenPair issue(User user);

  boolean isAccessTokenValid(String accessToken);

  TokenSubject verifyRefresh(String refreshToken); // Return subject + jti

  TokenPair rotate(User user, String oldRefreshJti); // Optional rotation
}
