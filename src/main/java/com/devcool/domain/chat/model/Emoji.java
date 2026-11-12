package com.devcool.domain.chat.model;

import com.devcool.domain.chat.exception.InvalidEmojiException;
import java.util.Objects;
import java.util.Set;

/** A reaction emoji as the client sent it; the constructor rejects anything else (400). */
public record Emoji(String value) {

  /** Long enough for ZWJ sequences such as a family, short enough to bound the column. */
  public static final int MAX_CODE_POINTS = 16;

  /** Glue, not emoji on their own: ZWJ, variation selector 16, combining keycap. */
  private static final Set<Integer> COMPONENTS = Set.of(0x200D, 0xFE0F, 0x20E3);

  /**
   * Every code point is an emoji or a component, and at least one is an emoji (a lone ZWJ is not).
   * A bare digit, '#' or '*' passes: {@link Character#isEmoji} counts them, harmless here.
   */
  public Emoji {
    if (Objects.isNull(value)
        || value.isEmpty()
        || value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
      throw new InvalidEmojiException();
    }
    boolean hasEmoji = false;
    for (int codePoint : value.codePoints().toArray()) {
      boolean emoji = Character.isEmoji(codePoint);
      if (!emoji && !COMPONENTS.contains(codePoint)) {
        throw new InvalidEmojiException();
      }
      hasEmoji |= emoji;
    }
    if (!hasEmoji) {
      throw new InvalidEmojiException();
    }
  }
}
