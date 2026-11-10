package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.model.enums.ContentType;
import org.springframework.stereotype.Component;

@Component
public class MarkdownMessageCreationStrategy extends AbstractTextMessageCreationStrategy {

  @Override
  public ContentType getSupportedType() {
    return ContentType.MARKDOWN;
  }
}
