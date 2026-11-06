package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.model.enums.ContentType;
import org.springframework.stereotype.Component;

@Component
public class ImageMessageCreationStrategy extends AbstractMediaMessageCreationStrategy {

  @Override
  public ContentType getSupportedType() {
    return ContentType.IMAGE;
  }
}
