package com.devcool.adapters.in.web.util;

import com.devcool.domain.channel.exception.InvalidChannelCursorException;
import com.devcool.domain.channel.model.ChannelCursor;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

/** Channel-list cursor token: base64url of {@code <ISO instant or empty>|<id>}. Not secret. */
public final class ChannelCursorCodec {

  private static final String SEPARATOR = "|";

  private ChannelCursorCodec() {}

  public static String encode(ChannelCursor cursor) {
    String time = cursor.lastMessageAt() == null ? "" : cursor.lastMessageAt().toString();
    String payload = time + SEPARATOR + cursor.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  /** Throws {@link InvalidChannelCursorException} on a malformed token. */
  public static ChannelCursor decode(String token) {
    try {
      String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      String[] parts = payload.split("\\" + SEPARATOR, -1);
      if (token.isEmpty() || parts.length != 2) {
        throw new InvalidChannelCursorException();
      }
      Instant time = parts[0].isEmpty() ? null : Instant.parse(parts[0]);
      long id = Long.parseLong(parts[1]);
      if (id < 1) {
        throw new InvalidChannelCursorException();
      }
      return new ChannelCursor(time, id);
    } catch (IllegalArgumentException | DateTimeException e) {
      // Bad base64 or id (NumberFormatException is an IllegalArgumentException).
      throw new InvalidChannelCursorException();
    }
  }
}
