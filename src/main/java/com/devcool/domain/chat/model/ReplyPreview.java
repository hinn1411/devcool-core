package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;

/**
 * The parent shown above a reply: {@code preview} is a text snippet, null for media or a deleted
 * parent.
 */
public record ReplyPreview(
    Long id,
    Long seq,
    Long senderId,
    String senderName,
    ContentType contentType,
    String preview,
    boolean deleted) {

  /** In code points, so an emoji (a surrogate pair) is never cut in half. */
  public static final int PREVIEW_CODE_POINTS = 100;

  /** Applies the preview rules to the parent's current state. */
  public static ReplyPreview of(
      Long id,
      Long seq,
      Long senderId,
      String senderName,
      ContentType contentType,
      String content,
      boolean deleted) {
    String preview = showsText(contentType, deleted) ? snippet(content) : null;
    return new ReplyPreview(id, seq, senderId, senderName, contentType, preview, deleted);
  }

  /** Only a live text parent quotes words; media would leak a storage path, a tombstone nothing. */
  private static boolean showsText(ContentType contentType, boolean deleted) {
    return !deleted && (contentType == ContentType.TEXT || contentType == ContentType.MARKDOWN);
  }

  private static String snippet(String content) {
    if (content == null || content.codePointCount(0, content.length()) <= PREVIEW_CODE_POINTS) {
      return content;
    }
    return content.substring(0, content.offsetByCodePoints(0, PREVIEW_CODE_POINTS));
  }
}
