package com.devcool.domain.media.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Kinds of uploadable media, each with its accepted content types and size limit. */
public enum MediaKind {
  /** Images: (0, 5 MB]. */
  IMAGE(Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp"), 5 * MediaKind.MB),
  /** Videos: (0, 10 MB), so the largest accepted size is one byte under 10 MB. */
  VIDEO(Map.of("video/mp4", ".mp4"), 10 * MediaKind.MB - 1);

  private static final long MB = 1024L * 1024L;

  /** Accepted content type → the key extension a presigned upload gets. */
  private final Map<String, String> extensions;

  private final long maxBytes;

  MediaKind(Map<String, String> extensions, long maxBytes) {
    this.extensions = extensions;
    this.maxBytes = maxBytes;
  }

  public static Optional<MediaKind> fromContentType(String contentType) {
    if (Objects.isNull(contentType)) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(kind -> kind.extensions.containsKey(contentType))
        .findFirst();
  }

  public boolean isSizeAllowed(long size) {
    return size > 0 && size <= maxBytes;
  }

  /** The key extension (with dot) for one of this kind's content types. */
  public String extensionFor(String contentType) {
    return extensions.get(contentType);
  }

  public long maxBytes() {
    return maxBytes;
  }
}
