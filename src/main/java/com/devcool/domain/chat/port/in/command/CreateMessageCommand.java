package com.devcool.domain.chat.port.in.command;

import com.devcool.domain.chat.model.enums.ContentType;
import java.util.UUID;

/**
 * {@code clientMsgId} is the sender's idempotency key: a retry with the same key saves nothing.
 * {@code replyToId} is null unless the message replies to another of the same channel.
 */
public record CreateMessageCommand(
    String content,
    ContentType contentType,
    Long channelId,
    Long userId,
    UUID clientMsgId,
    Long replyToId) {

  /** Not a reply. */
  public CreateMessageCommand(
      String content, ContentType contentType, Long channelId, Long userId, UUID clientMsgId) {
    this(content, contentType, channelId, userId, clientMsgId, null);
  }
}
