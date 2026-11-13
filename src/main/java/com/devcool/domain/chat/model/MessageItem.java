package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
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

  /** Emojis by first reaction; empty for none and for a tombstone. Copied in and out. */
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private List<ReactionSummary> reactions;

  private Long userId;
  private String senderAvatar;
  private String senderName;

  public List<ReactionSummary> getReactions() {
    return reactions == null ? List.of() : List.copyOf(reactions);
  }

  public void setReactions(List<ReactionSummary> reactions) {
    this.reactions = List.copyOf(reactions);
  }

  /** Lombok fills in the rest; this method copies, like the setter. */
  public static class MessageItemBuilder {
    public MessageItemBuilder reactions(List<ReactionSummary> reactions) {
      this.reactions = List.copyOf(reactions);
      return this;
    }
  }
}
