package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.model.enums.ContentType;
import org.springframework.stereotype.Component;

@Component
public class MarkdownMessageCreationStrategy extends AbstractMessageCreationStrategy {

  @Override
  public ContentType getSupportedType() {
    throw new UnsupportedOperationException();
  }
}
