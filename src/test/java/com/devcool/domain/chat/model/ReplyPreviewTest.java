package com.devcool.domain.chat.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.chat.model.enums.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The reply preview rules (P3-T08): which parents quote text, and how much. */
class ReplyPreviewTest {

  private static final String EMOJI = "🙂"; // one code point, two chars

  @ParameterizedTest
  @EnumSource(
      value = ContentType.class,
      names = {"TEXT", "MARKDOWN"})
  void liveTextParent_quotesItsContent(ContentType type) {
    assertThat(preview(type, "# hi", false).preview()).isEqualTo("# hi");
  }

  @Test
  void exactly100CodePoints_isKeptWhole() {
    assertThat(preview(ContentType.TEXT, "x".repeat(100), false).preview())
        .isEqualTo("x".repeat(100));
  }

  @Test
  void longerContent_isCutTo100CodePoints() {
    assertThat(preview(ContentType.TEXT, "x".repeat(150), false).preview())
        .isEqualTo("x".repeat(100));
  }

  @Test
  void emojiAcrossTheLimit_isKeptWhole_notSplitIntoALoneSurrogate() {
    // 99 + 2 code points. A char cut at 100 would keep only the emoji's high surrogate.
    String content = "x".repeat(99) + EMOJI + EMOJI;

    String preview = preview(ContentType.TEXT, content, false).preview();

    assertThat(preview).isEqualTo("x".repeat(99) + EMOJI);
    assertThat(preview.codePointCount(0, preview.length())).isEqualTo(100);
  }

  @ParameterizedTest
  @EnumSource(
      value = ContentType.class,
      names = {"IMAGE", "VIDEO"})
  void mediaParent_quotesNoText_butKeepsItsType(ContentType type) {
    ReplyPreview preview = preview(type, "channels/1/cat.png", false);

    assertThat(preview.preview()).isNull();
    assertThat(preview.contentType()).isEqualTo(type);
  }

  @Test
  void deletedParent_quotesNothing_andSaysSo() {
    ReplyPreview preview = preview(ContentType.TEXT, "oops", true);

    assertThat(preview.preview()).isNull();
    assertThat(preview.deleted()).isTrue();
  }

  @Test
  void identityFields_areCopied() {
    assertThat(ReplyPreview.of(4L, 6L, 8L, "Bob", ContentType.TEXT, "hi", false))
        .isEqualTo(new ReplyPreview(4L, 6L, 8L, "Bob", ContentType.TEXT, "hi", false));
  }

  private static ReplyPreview preview(ContentType type, String content, boolean deleted) {
    return ReplyPreview.of(1L, 2L, 3L, "Alice", type, content, deleted);
  }
}
