package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MessageItem {
  private Long id;
  private Long seq;
  private String content;
  private ContentType contentType;
  private Instant createdTime;
  private Integer version;
  private Instant editedTime;
  private Instant deletedTime;
  private ReplyPreview replyTo;

  /** Emojis by first reaction; empty for none and for a tombstone. */
  @Builder.Default private List<ReactionSummary> reactions = List.of();

  private Long userId;
  private String senderAvatar;
  private String senderName;
}
