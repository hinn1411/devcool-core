package com.devcool.adapters.out.jwt;

import com.devcool.adapters.out.jwt.enums.TokenType;
import com.devcool.domain.auth.model.AccessClaims;
import com.devcool.domain.auth.model.RefreshToken;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.auth.port.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.sql.Date;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenIssuerAdapter implements TokenIssuerPort {

  private static final Logger log = LoggerFactory.getLogger(TokenIssuerAdapter.class);
  private final byte[] accessKey;
  private final byte[] refreshKey;

  private static final long accessTtlSec = 3600; // 15 min
  private static final long refreshTtlSec = RefreshToken.TTL.toSeconds();

  private static final String TYPE = "type";
  private static final String ROLE = "role";

  public TokenIssuerAdapter(
      @Value("${security.jwt.access-secret}") String accessSecret,
      @Value("${security.jwt.refresh-secret}") String refreshSecret) {
    // Defensive checks (fail fast with a clear message)
    if (Objects.isNull(accessSecret) || accessSecret.isBlank()) {
      throw new IllegalStateException(
          "Missing property security.jwt.access-secret (env JWT_ACCESS_SECRET)");
    }
    if (Objects.isNull(refreshSecret) || refreshSecret.isBlank()) {
      throw new IllegalStateException(
          "Missing property security.jwt.refresh-secret (env JWT_REFRESH_SECRET)");
    }

    accessSecret = accessSecret.trim();
    refreshSecret = refreshSecret.trim();

    this.accessKey = Base64.getDecoder().decode(accessSecret);
    this.refreshKey = Base64.getDecoder().decode(refreshSecret);

    // Optional: enforce minimum lengths (HS256: ≥32 bytes)
    if (accessKey.length < 32) {
      throw new IllegalStateException("JWT access secret too short; need >= 32 bytes for HS256");
    }
    if (refreshKey.length < 32) {
      throw new IllegalStateException("JWT refresh secret too short; need >= 32 bytes for HS256");
    }
  }

  @Override
  public TokenPair issue(User user) {
    Instant now = Instant.now();
    String accessToken =
        sign(user, now, accessTtlSec, accessKey, TokenType.ACCESS.name(), newTokenId());
    String refreshJti = newTokenId();
    String refreshToken =
        sign(user, now, refreshTtlSec, refreshKey, TokenType.REFRESH.name(), refreshJti);
    return new TokenPair(accessToken, refreshToken, refreshJti);
  }

  @Override
  public Optional<AccessClaims> verifyAccess(String accessToken) {
    var jwt = verify(accessToken, accessKey, TokenType.ACCESS.name());
    if (Objects.isNull(jwt)) {
      return Optional.empty();
    }

    try {
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      return Optional.of(
          new AccessClaims(
              Integer.valueOf(claims.getSubject()),
              claims.getIntegerClaim("version"),
              claims.getStringClaim(ROLE)));
    } catch (ParseException | NumberFormatException e) {
      log.warn("Cannot read access token claims");
      return Optional.empty();
    }
  }

  @Override
  public Optional<TokenSubject> verifyRefresh(String refreshToken) {
    var jwt = verify(refreshToken, refreshKey, TokenType.REFRESH.name());
    if (Objects.isNull(jwt)) {
      return Optional.empty();
    }

    try {
      String subject = jwt.getJWTClaimsSet().getSubject();
      String jti = jwt.getJWTClaimsSet().getJWTID();
      return Optional.of(new TokenSubject(subject, jti));
    } catch (ParseException e) {
      log.warn("Cannot read refresh token claims");
      return Optional.empty();
    }
  }

  @Override
  public TokenPair rotate(User user, String oldRefreshJti) {
    return issue(user);
  }

  private static String newTokenId() {
    return UUID.randomUUID().toString();
  }

  private String sign(
      User user, Instant issuedTime, long ttlSec, byte[] key, String tokenType, String tokenId) {
    String userId = String.valueOf(user.getId());
    String userRole = Optional.ofNullable(user.getRole()).map(Enum::name).orElse(null);
    try {
      Instant expiredTime = issuedTime.plusSeconds(ttlSec);
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(userId)
              .issueTime(Date.from(issuedTime))
              .expirationTime(Date.from(expiredTime))
              .jwtID(tokenId)
              .audience("devcool-api")
              .claim(TYPE, tokenType)
              .claim(ROLE, userRole)
              .claim("version", user.getTokenVersion())
              .build();
      MACSigner signer = new MACSigner(key);
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("JWT signing failed", e);
    }
  }

  private SignedJWT verify(String token, byte[] key, String expectedTokenType) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      boolean isTokenVerified = jwt.verify(new MACVerifier(key));
      if (!isTokenVerified) {
        log.warn("Token is invalid!");
        return null;
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      if (claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
        log.warn("Token is expired!");
        return null;
      }

      if (!Objects.equals(claims.getStringClaim("type"), expectedTokenType)) {
        log.warn("Token type is mismatched!");
        return null;
      }

      if (!claims.getAudience().contains("devcool-api")) {
        log.warn("Invalid audience!");
        return null;
      }

      return jwt;
    } catch (ParseException e) {
      log.warn("Cannot parse token");
      return null;
    } catch (Exception e) {
      log.error("Cannot verify token. Cause by", e);
      return null;
    }
  }
}
