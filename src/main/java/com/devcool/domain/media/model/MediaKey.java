package com.devcool.domain.media.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The S3 object key of an uploaded media file: {@code
 * channel/{channelId}/{yyyy/MM/dd}/{uuid}{ext}}. The channel id in the key is what authorises
 * access to the object, so a key that does not match in full is rejected rather than parsed
 * loosely.
 */
public final class MediaKey {

  /** Lowercase, without the dot. */
  public static final String ALLOWED_EXTENSIONS = "jpg|jpeg|png|webp|mp4";

  private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private static final Pattern PATTERN =
      Pattern.compile(
          "channel/([1-9]\\d{0,8})"
              + "/\\d{4}/\\d{2}/\\d{2}"
              + "/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
              + "(?:\\.(?:"
              + ALLOWED_EXTENSIONS
              + "))?");

  private MediaKey() {}

  /**
   * A fresh key; {@code extension} is {@code ""} or one of {@link #ALLOWED_EXTENSIONS} with a dot.
   */
  public static String generate(long channelId, LocalDate date, String extension) {
    return "channel/%d/%s/%s%s"
        .formatted(channelId, date.format(DATE_PATH), UUID.randomUUID(), extension);
  }

  /** The channel the key belongs to, or empty if the key isn't exactly the generated shape. */
  public static Optional<Long> channelIdOf(String key) {
    if (Objects.isNull(key)) {
      return Optional.empty();
    }
    Matcher matcher = PATTERN.matcher(key);
    return matcher.matches() ? Optional.of(Long.valueOf(matcher.group(1))) : Optional.empty();
  }
}
