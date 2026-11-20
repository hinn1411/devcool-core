package com.devcool.adapters.in.websocket.dto;

import com.devcool.domain.chat.model.MessageEvent;
import java.time.Instant;

/** The MESSAGE frame pushed to subscribers. */
public record MessagePush(
    String type,
    Long channelId,
    Long senderId,
    String contentType,
    String content,
    Long messageId,
    Long seq,
    Instant createdTime,
    Long replyToId) {

  public static MessagePush from(MessageEvent event) {
    return new MessagePush(
        "MESSAGE",
        event.channelId(),
        event.senderId(),
        event.contentType().name(),
        event.content(),
        event.messageId(),
        event.seq(),
        event.createdTime(),
        event.replyToId());
  }
}
