package com.devcool.adapters.out.jwt.util;

import com.devcool.adapters.out.crypto.util.HashUtils;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.user.model.User;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtUtils {

  private JwtUtils() {}

  private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

  public static String jtiFrom(String token) {
    JWTClaimsSet claims = getClaims(token);
    return claims.getJWTID();
  }

  public static String subjectFrom(String token) {
    JWTClaimsSet claims = getClaims(token);
    return claims.getSubject();
  }

  public static String roleFrom(String token) {
    JWTClaimsSet claims = getClaims(token);
    try {
      return claims.getStringClaim("role");
    } catch (ParseException e) {
      log.warn("Cannot get role from token!");
      return null;
    }
  }

  public static String  userIdFrom(String token) {
    return subjectFrom(token);
  }

  public static Integer versionFrom(String token) {
    JWTClaimsSet claims = getClaims(token);
    try {
      return claims.getIntegerClaim("version");
    } catch (ParseException e) {
      log.warn("Cannot get version from token!");
      return -1;
    }
  }

  private static JWTClaimsSet getClaims(String token) {
    try {
      return SignedJWT.parse(token).getJWTClaimsSet();
    } catch (Exception e) {
      log.warn("Invalid JWT token!");
      throw new IllegalArgumentException("Invalid JWT token", e);
    }
  }

  private static String hashJtiFrom(String refreshToken) {
    return HashUtils.sha256(JwtUtils.jtiFrom(refreshToken));
  }

  public static RefreshToken buildRefreshToken(User user, TokenPair tokenPair) {
    return RefreshToken.builder()
        .jti(hashJtiFrom(tokenPair.refreshToken()))
        .userId(user.getId())
        .issuedTime(Instant.now())
        .expiredTime(Instant.now().plus(7, ChronoUnit.DAYS))
        .consumedTime(null)
        .build();
  }
}
