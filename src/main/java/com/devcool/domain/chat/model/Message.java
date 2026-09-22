package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.media.model.Media;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Message {
  private Integer id;
  private Integer senderId;
  private String content;
  private ContentType contentType;
  private Instant createdTime;
  private Instant deletedTime;
  private Instant editedTime;
  private Integer channelId;
  private Media media;
}
