package com.devcool.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devcool.domain.chat.exception.InvalidEmojiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The reaction emoji rule (P3-T09). */
class EmojiTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "👍", // one code point
        "👍🏽", // + skin-tone modifier
        "👨‍👩‍👧", // ZWJ sequence, 5 code points
        "❤️", // text-default symbol + VS16
        "🇻🇳", // flag: two regional indicators
        "1️⃣" // keycap: digit + VS16 + U+20E3
      })
  void realEmoji_areAccepted_andKeptAsSent(String raw) {
    assertThat(new Emoji(raw).value()).isEqualTo(raw);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "a", "👍a", "<b>", "👍 👍", "‍", "\uD83D"})
  void nonEmoji_isRejected(String raw) {
    assertThatThrownBy(() -> new Emoji(raw)).isInstanceOf(InvalidEmojiException.class);
  }

  @Test
  void exactlyTheCap_isAccepted_oneMoreIsRejected() {
    String atCap = "👍".repeat(Emoji.MAX_CODE_POINTS);

    assertThat(new Emoji(atCap).value()).isEqualTo(atCap);
    assertThatThrownBy(() -> new Emoji(atCap + "👍")).isInstanceOf(InvalidEmojiException.class);
  }
}
