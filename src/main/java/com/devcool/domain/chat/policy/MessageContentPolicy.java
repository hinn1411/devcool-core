package com.devcool.domain.chat.policy;

import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import java.util.Objects;

/** Rules for content that is the message itself (TEXT, MARKDOWN): on send (P3-T15) and edit. */
public final class MessageContentPolicy {

  /** {@code message.content} is {@code varchar(1000)}: PG counts characters (code points). */
  public static final int MAX_CONTENT_CODE_POINTS = 1000;

  private MessageContentPolicy() {}

  public static void requireValidText(String content) {
    if (Objects.isNull(content) || content.isBlank()) {
      throw new InvalidMessageConfigException("Message content must not be blank");
    }
    if (content.codePointCount(0, content.length()) > MAX_CONTENT_CODE_POINTS) {
      throw new InvalidMessageConfigException(
          "Message content exceeds " + MAX_CONTENT_CODE_POINTS + " characters");
    }
    if (content.indexOf('\0') >= 0) { // PG text types reject 0x00: a raw DB error otherwise
      throw new InvalidMessageConfigException("Message content contains a NUL character");
    }
  }
}
