package com.devcool.adapters.in.web.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.channel.exception.InvalidChannelCursorException;
import com.devcool.domain.channel.model.ChannelCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** P3-T10 channel-list cursor codec. */
class ChannelCursorCodecTest {

  @Test
  void roundTrip_datedCursor_keepsMicrosecondsAndId() {
    ChannelCursor cursor = new ChannelCursor(Instant.parse("2026-11-13T10:00:00.123456Z"), 42L);

    assertThat(ChannelCursorCodec.decode(ChannelCursorCodec.encode(cursor))).isEqualTo(cursor);
  }

  @Test
  void roundTrip_cursorInTheEmptyChannelGroup_keepsTheNullTime() {
    ChannelCursor cursor = new ChannelCursor(null, 7L);

    assertThat(ChannelCursorCodec.decode(ChannelCursorCodec.encode(cursor))).isEqualTo(cursor);
  }

  @Test
  void encode_isUrlSafe() {
    String token =
        ChannelCursorCodec.encode(
            new ChannelCursor(Instant.parse("2026-11-13T10:00:00.123456Z"), Long.MAX_VALUE));

    assertThat(token).matches("[A-Za-z0-9_-]+");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not base64 !!", "%%%"})
  void decode_notBase64_isInvalid(String token) {
    assertInvalid(token);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "no-separator",
        "2026-11-13T10:00:00Z|abc",
        "yesterday|5",
        "2026-11-13T10:00:00Z|",
        "|-3"
      })
  void decode_wellEncodedButMalformed_isInvalid(String payload) {
    assertInvalid(
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
  }

  private static void assertInvalid(String token) {
    assertThatThrownBy(() -> ChannelCursorCodec.decode(token))
        .isInstanceOf(InvalidChannelCursorException.class);
  }
}
