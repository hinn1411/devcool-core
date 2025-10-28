package com.devcool.adapters.out.jwt;

import com.devcool.adapters.out.jwt.enums.TokenType;
import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.auth.out.TokenIssuerPort;
import com.devcool.domain.user.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
public class TokenIssuerAdapter implements TokenIssuerPort {

    private final byte[] accessKey;
    private final byte[] refreshKey;

    private final static long accessTtlSec = 900;   // 15 min
    private final static long refreshTtlSec = 1209600; // 14 days

    public TokenIssuerAdapter(@Value("${security.jwt.access-secret}") String accessSecret,
                              @Value("${security.jwt.refresh-secret}") String refreshSecret) {
        // Defensive checks (fail fast with a clear message)
        if (Objects.isNull(accessSecret) || accessSecret.isBlank()) {
            throw new IllegalStateException("Missing property security.jwt.access-secret (env JWT_ACCESS_SECRET)");
        }
        if (Objects.isNull(refreshSecret) || refreshSecret.isBlank()) {
            throw new IllegalStateException("Missing property security.jwt.refresh-secret (env JWT_REFRESH_SECRET)");
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
        String accessToken = sign(String.valueOf(user.getId()), now, accessTtlSec, accessKey, TokenType.ACCESS.name(), null);
        String refreshTokenId = UUID.randomUUID().toString();
        String refreshToken = sign(String.valueOf(user.getId()), now, refreshTtlSec, refreshKey, TokenType.REFRESH.name(), refreshTokenId);
        return new TokenPair(accessToken, refreshToken);
    }

    @Override
    public boolean isAccessTokenValid(String accessToken) {
        return Objects.nonNull(verify(accessToken, accessKey, TokenType.ACCESS.name()));
    }

    @Override
    public TokenSubject verifyRefresh(String refreshToken) {
        var jwt = verify(refreshToken, refreshKey, TokenType.REFRESH.name());
        if (Objects.isNull(jwt)) {
            return null;
        }

        try {
            String subject = jwt.getJWTClaimsSet().getSubject();
            String jti = jwt.getJWTClaimsSet().getJWTID();
            return new TokenSubject(subject, jti);
        } catch (ParseException e) {
            throw new BadCredentialsException("Cannot verify refresh token", e);
        }
    }

    @Override
    public TokenPair rotate(User user, String oldRefreshJti) {
        return issue(user);
    }



    private String sign(String sub, Instant issuedTime, long ttlSec, byte[] key, String tokenType, String jti) {
        try {
            Instant expiredTime = issuedTime.plusSeconds(ttlSec);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sub)
                    .issueTime(Date.from(issuedTime))
                    .expirationTime(Date.from(expiredTime))
                    .jwtID(jti)
                    .audience("devcool-api")
                    .claim("type", tokenType)
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
                return null;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return null;
            }

            if (!Objects.equals(claims.getStringClaim("typ"), expectedTokenType)) {
                return  null;
            }

            if (!claims.getAudience().contains("devcool-api")) {
                return  null;
            }

            return jwt;
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid token", e);
        }
    }
}
