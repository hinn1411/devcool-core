package com.devcool.domain.chat.model;

import com.devcool.domain.channel.model.Channel;
import com.devcool.domain.chat.model.enums.ContentType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class Message {
  private Integer id;
  private Integer senderUserId;
  private String content;
  private ContentType contentType;
  private Instant createdTime;
  private Instant deletedTime;
  private Instant editedTime;
  private Channel channel;
}
