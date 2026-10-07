package com.devcool.adapters.out.crypto;

import com.devcool.domain.auth.port.out.TokenHashPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class Sha256TokenHashAdapter implements TokenHashPort {

  @Override
  public String hash(String tokenId) {
    if (Objects.isNull(tokenId)) {
      throw new IllegalArgumentException("Input cannot be null");
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(tokenId.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
