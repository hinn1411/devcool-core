package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.exception.InvalidMessageConfigException;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import java.util.Objects;

/** Base for types whose content is the message itself (TEXT, MARKDOWN). */
public abstract class AbstractTextMessageCreationStrategy extends AbstractMessageCreationStrategy {

  /** {@code message.content} is {@code varchar(1000)}: PG counts characters (code points). */
  static final int MAX_CONTENT_CODE_POINTS = 1000;

  @Override
  public void validate(CreateMessageCommand command) {
    String content = command.content();
    if (Objects.isNull(content) || content.isBlank()) {
      throw new InvalidMessageConfigException("Message content must not be blank");
    }
    if (content.codePointCount(0, content.length()) > MAX_CONTENT_CODE_POINTS) {
      throw new InvalidMessageConfigException(
          "Message content exceeds " + MAX_CONTENT_CODE_POINTS + " characters");
    }
    if (content.indexOf('\0') >= 0) { // PG text types reject 0x00: a raw DB error otherwise
      throw new InvalidMessageConfigException("Message content contains a NUL character");
    }
  }
}
