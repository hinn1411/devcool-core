package com.devcool.domain.chat.model;

import com.devcool.domain.chat.model.enums.ContentType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MessageItem {
  private Integer id;
  private String content;
  private ContentType contentType;
  private Instant createdTime;
  private Integer userId;
  private String senderAvatar;
  private String senderName;
}
