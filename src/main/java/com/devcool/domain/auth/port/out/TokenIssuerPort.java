package com.devcool.domain.auth.port.out;

import com.devcool.domain.auth.model.AccessClaims;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.user.model.User;
import java.util.Optional;

public interface TokenIssuerPort {
  TokenPair issue(User user);

  // Claims, or empty when the token is missing, malformed, forged, expired or not an access token.
  Optional<AccessClaims> verifyAccess(String accessToken);

  // Subject + jti, or empty when the token is missing, malformed, forged, expired or not a
  // refresh token.
  Optional<TokenSubject> verifyRefresh(String refreshToken);

  TokenPair rotate(User user, String oldRefreshJti); // Optional rotation
}
