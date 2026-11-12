package com.devcool.domain.chat.model;

/** A reaction emoji as the client sent it; the constructor rejects anything else (400). */
public record Emoji(String value) {

  /** Long enough for ZWJ sequences such as a family, short enough to bound the column. */
  public static final int MAX_CODE_POINTS = 16;

  public Emoji {
    throw new UnsupportedOperationException("P3-T09 Red");
  }
}
