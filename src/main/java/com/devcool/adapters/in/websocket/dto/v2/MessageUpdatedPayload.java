package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.MessageUpdatedEvent;
import java.time.Instant;

/** {@code MESSAGE_UPDATED} */
public record MessageUpdatedPayload(
    Long messageId,
    Long seq,
    Instant createdTime,
    String content,
    int version,
    Instant editedTime) {

  public static MessageUpdatedPayload from(MessageUpdatedEvent event) {
    return new MessageUpdatedPayload(
        event.messageId(),
        event.seq(),
        event.createdTime(),
        event.content(),
        event.version(),
        event.editedTime());
  }
}
