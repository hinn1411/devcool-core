package com.devcool.domain.media.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S3 key {@code channel/{channelId}/{yyyy/MM/dd}/{uuid}{ext}}. The channel id authorises access, so
 * only an exact match parses.
 */
public final class MediaKey {

  private static final String EXTENSIONS = "jpg|jpeg|png|webp|mp4";
  private static final Pattern EXTENSION = Pattern.compile("\\.(?:" + EXTENSIONS + ")");
  private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
  private static final Pattern PATTERN =
      Pattern.compile(
          "channel/([1-9]\\d{0,8})"
              + "/\\d{4}/\\d{2}/\\d{2}"
              + "/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
              + "(?:\\.(?:"
              + EXTENSIONS
              + "))?");

  private MediaKey() {}

  /** {@code extension} is {@code ""} or an allowed one, with the dot. */
  public static String generate(long channelId, LocalDate date, String extension) {
    return "channel/%d/%s/%s%s"
        .formatted(channelId, date.format(DATE_PATH), UUID.randomUUID(), extension);
  }

  /** {@code extension} lowercase, with the dot, e.g. {@code ".png"}. */
  public static boolean isAllowedExtension(String extension) {
    return EXTENSION.matcher(extension).matches();
  }

  /** Empty unless the key is exactly the generated shape. */
  public static Optional<Long> channelIdOf(String key) {
    if (Objects.isNull(key)) {
      return Optional.empty();
    }
    Matcher matcher = PATTERN.matcher(key);
    return matcher.matches() ? Optional.of(Long.valueOf(matcher.group(1))) : Optional.empty();
  }
}
