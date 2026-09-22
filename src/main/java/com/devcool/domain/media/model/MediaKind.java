package com.devcool.domain.media.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Kinds of uploadable media, each with its accepted content types and size limit. */
public enum MediaKind {
  /** Images: (0, 5 MB]. */
  IMAGE(Set.of("image/jpeg", "image/png", "image/webp"), 5 * MediaKind.MB),
  /** Videos: (0, 10 MB), so the largest accepted size is one byte under 10 MB. */
  VIDEO(Set.of("video/mp4"), 10 * MediaKind.MB - 1);

  private static final long MB = 1024L * 1024L;

  private final Set<String> contentTypes;
  private final long maxBytes;

  MediaKind(Set<String> contentTypes, long maxBytes) {
    this.contentTypes = contentTypes;
    this.maxBytes = maxBytes;
  }

  public static Optional<MediaKind> fromContentType(String contentType) {
    if (Objects.isNull(contentType)) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(kind -> kind.contentTypes.contains(contentType))
        .findFirst();
  }

  public boolean isSizeAllowed(long size) {
    return size > 0 && size <= maxBytes;
  }

  public long maxBytes() {
    return maxBytes;
  }
}
