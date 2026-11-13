package com.devcool.domain.chat.model;

import com.devcool.domain.chat.exception.InvalidEmojiException;
import java.util.Objects;
import java.util.Set;

/** A reaction emoji, kept as sent; anything else is a 400. */
public record Emoji(String value) {

  /** Fits ZWJ sequences; matches the varchar(16) column. */
  public static final int MAX_CODE_POINTS = 16;

  /** ZWJ, VS16, keycap: valid only next to an emoji. */
  private static final Set<Integer> COMPONENTS = Set.of(0x200D, 0xFE0F, 0x20E3);

  /** Emoji or component code points, at least one emoji. A bare digit, '#' or '*' passes. */
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
