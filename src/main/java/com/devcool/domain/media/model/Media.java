package com.devcool.domain.media.model;

import com.devcool.domain.chat.model.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class Media {
  private Integer id;
  private String path;
  private Instant createdTime;
  private Message message;
}
