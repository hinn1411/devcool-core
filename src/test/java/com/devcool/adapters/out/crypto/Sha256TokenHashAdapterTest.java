package com.devcool.adapters.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256TokenHashAdapterTest {

  private final Sha256TokenHashAdapter adapter = new Sha256TokenHashAdapter();

  @Test
  void hash_isTheUnpaddedBase64UrlSha256OfTheInput() {
    // SHA-256("abc"), the FIPS 180-2 test vector, in URL-safe Base64 without padding.
    assertThat(adapter.hash("abc")).isEqualTo("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0");
  }

  @Test
  void hash_isDeterministicAndNeverReturnsTheInput() {
    assertThat(adapter.hash("jti-1")).isEqualTo(adapter.hash("jti-1")).isNotEqualTo("jti-1");
  }

  @Test
  void hash_nullInput_isRejected() {
    assertThatThrownBy(() -> adapter.hash(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
