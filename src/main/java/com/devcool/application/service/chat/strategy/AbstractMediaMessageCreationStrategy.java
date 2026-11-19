package com.devcool.application.service.chat.strategy;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.port.in.command.CreateMessageCommand;
import com.devcool.domain.media.exception.InvalidObjectKeyException;
import com.devcool.domain.media.model.Media;
import com.devcool.domain.media.model.MediaKey;
import java.time.Instant;

/**
 * Base for media messages: the command's {@code content} is the S3 object key, so it is moved to
 * {@link Media#getPath()} and the message's own content is cleared.
 */
public abstract class AbstractMediaMessageCreationStrategy extends AbstractMessageCreationStrategy {

  /**
   * The key must be one the server generated for this very channel (P3-T14): the client got it from
   * presign-upload, so anything else is a bug or a probe. Runs before authz and the seq bump.
   */
  @Override
  public void validate(CreateMessageCommand command) {
    MediaKey.channelIdOf(command.content())
        .filter(keyChannel -> keyChannel.equals(command.channelId()))
        .orElseThrow(InvalidObjectKeyException::new);
  }

  @Override
  public Message buildMessage(CreateMessageCommand command) {
    Message message = super.buildMessage(command);
    Media media = Media.builder().path(command.content()).createdTime(Instant.now()).build();
    message.setMedia(media);
    message.setContent(null);
    return message;
  }
}
