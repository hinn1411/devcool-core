package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.MessageDeletedEvent;
import java.time.Instant;

/** {@code MESSAGE_DELETED}; the channel id is on the envelope. */
public record MessageDeletedPayload(Long messageId, Long seq, Instant createdTime) {

  public static MessageDeletedPayload from(MessageDeletedEvent event) {
    return new MessageDeletedPayload(event.messageId(), event.seq(), event.createdTime());
  }
}
