package com.devcool.application.service.chat.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.enums.ContentType;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** P3-T15: markdown is stored as its raw source; rendering and sanitizing are the client's job. */
class MarkdownMessageCreationStrategyTest {

  private final MarkdownMessageCreationStrategy strategy = new MarkdownMessageCreationStrategy();

  @Test
  void supportsMarkdown() {
    assertThat(strategy.getSupportedType()).isEqualTo(ContentType.MARKDOWN);
  }

  @Test
  void buildMessage_keepsTheRawSource() {
    String source = "# hi **x** <img src=x onerror=alert(1)> [l](javascript:alert(1))";

    Message message =
        strategy.buildMessage(
            new CreateMessageCommand(source, ContentType.MARKDOWN, 1L, 10L, UUID.randomUUID()));

    assertThat(message.getContent()).isEqualTo(source); // no escaping, no stripping
    assertThat(message.getContentType()).isEqualTo(ContentType.MARKDOWN);
    assertThat(message.getMedia()).isNull();
  }
}
