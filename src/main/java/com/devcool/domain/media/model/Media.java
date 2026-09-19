package com.devcool.domain.media.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Media {
  private Integer id;
  private String path;
  private Instant createdTime;
}
