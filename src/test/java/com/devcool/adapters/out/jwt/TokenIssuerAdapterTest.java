package com.devcool.adapters.out.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.model.TokenSubject;
import com.devcool.domain.user.model.User;
import com.devcool.domain.user.model.enums.Role;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TokenIssuerAdapterTest {

  private static final byte[] ACCESS_KEY = key("access-key-0123456789abcdef0123456789");
  private static final byte[] REFRESH_KEY = key("refresh-key-0123456789abcdef012345678");
  private static final byte[] OTHER_KEY = key("another-key-0123456789abcdef012345678");
  private static final User USER = User.builder().id(7).role(Role.USER).build();

  private final TokenIssuerAdapter adapter =
      new TokenIssuerAdapter(b64(ACCESS_KEY), b64(REFRESH_KEY));

  private static byte[] key(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String b64(byte[] key) {
    return Base64.getEncoder().encodeToString(key);
  }

  // A token shaped like the adapter's own, so each test can break exactly one property.
  private static String refreshToken(byte[] signingKey, String type, Instant expiresAt)
      throws JOSEException {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("7")
            .jwtID("jti-1")
            .audience("devcool-api")
            .expirationTime(Date.from(expiresAt))
            .claim("type", type)
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(signingKey));
    return jwt.serialize();
  }

  private static Instant inOneHour() {
    return Instant.now().plusSeconds(3600);
  }

  @Test
  void verifyRefresh_refreshTokenItIssued_returnsSubjectAndJti() {
    TokenPair pair = adapter.issue(USER);

    Optional<TokenSubject> subject = adapter.verifyRefresh(pair.refreshToken());

    assertThat(subject).isPresent();
    assertThat(subject.get().userId()).isEqualTo("7");
    assertThat(subject.get().jti()).isNotBlank();
  }

  @Test
  void verifyRefresh_accessTokenItIssued_returnsEmpty() {
    TokenPair pair = adapter.issue(USER);

    assertThat(adapter.verifyRefresh(pair.accessToken())).isEmpty();
  }

  @Test
  void verifyRefresh_wellFormedTokenSignedWithTheRefreshKey_returnsSubject() throws JOSEException {
    String token = refreshToken(REFRESH_KEY, "REFRESH", inOneHour());

    assertThat(adapter.verifyRefresh(token)).contains(new TokenSubject("7", "jti-1"));
  }

  @Test
  void verifyRefresh_signedWithAnotherKey_returnsEmpty() throws JOSEException {
    String forged = refreshToken(OTHER_KEY, "REFRESH", inOneHour());

    assertThat(adapter.verifyRefresh(forged)).isEmpty();
  }

  @Test
  void verifyRefresh_expired_returnsEmpty() throws JOSEException {
    String expired = refreshToken(REFRESH_KEY, "REFRESH", Instant.now().minusSeconds(60));

    assertThat(adapter.verifyRefresh(expired)).isEmpty();
  }

  @Test
  void verifyRefresh_wrongTokenType_returnsEmpty() throws JOSEException {
    String accessTyped = refreshToken(REFRESH_KEY, "ACCESS", inOneHour());

    assertThat(adapter.verifyRefresh(accessTyped)).isEmpty();
  }

  @Test
  void verifyRefresh_notAJwt_returnsEmpty() {
    assertThat(adapter.verifyRefresh("not-a-jwt")).isEmpty();
  }

  @Test
  void verifyRefresh_null_returnsEmpty() {
    assertThat(adapter.verifyRefresh(null)).isEmpty();
  }
}
