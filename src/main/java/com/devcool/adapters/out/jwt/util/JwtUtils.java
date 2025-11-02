package com.devcool.adapters.out.jwt.util;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
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

  private static JWTClaimsSet getClaims(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      return jwt.getJWTClaimsSet();
    } catch (Exception e) {
      log.warn("Invalid JWT token!");
      throw new IllegalArgumentException("Invalid JWT token", e);
    }
  }
}
