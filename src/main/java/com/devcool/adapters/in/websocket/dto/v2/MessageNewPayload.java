package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.MessageEvent;
import java.time.Instant;

/** {@code MESSAGE_NEW}; the channel id is on the envelope. */
public record MessageNewPayload(
    Long messageId,
    Long seq,
    Long senderId,
    String contentType,
    String content,
    Long replyToId,
    Instant createdTime) {

  public static MessageNewPayload from(MessageEvent event) {
    throw new UnsupportedOperationException();
  }
}
