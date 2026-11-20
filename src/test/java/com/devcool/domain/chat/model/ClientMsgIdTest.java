package com.devcool.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.chat.exception.InvalidClientMsgIdException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientMsgIdTest {

  private static final String KEY = "7f1c2a9e-0000-4000-8000-000000000001";

  @Test
  void parse_canonicalUuid_wrapsIt() {
    assertThat(ClientMsgId.parse(KEY).value()).isEqualTo(UUID.fromString(KEY));
  }

  @Test
  void parse_upperCase_isTheSameKeyInCanonicalForm() {
    ClientMsgId upper = ClientMsgId.parse(KEY.toUpperCase());

    assertThat(upper).isEqualTo(ClientMsgId.parse(KEY));
    assertThat(upper.toString()).isEqualTo(KEY);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "not-a-uuid",
        "1-1-1-1-1", // UUID.fromString accepts it; not a client key
        "7f1c2a9e-0000-4000-8000-00000000000g", // right length, not hex
        "7f1c2a9e-0000-4000-8000-0000000000011" // 37 chars
      })
  void parse_rejectsAnythingButTheCanonicalForm(String raw) {
    assertThatThrownBy(() -> ClientMsgId.parse(raw))
        .isInstanceOf(InvalidClientMsgIdException.class)
        .hasMessage("clientMsgId must be a canonical UUID"); // fixed text: the input is not echoed
  }
}
