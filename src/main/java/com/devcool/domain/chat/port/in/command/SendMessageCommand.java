package com.devcool.domain.chat.port.in.command;

import com.devcool.domain.chat.model.ClientMsgId;
import com.devcool.domain.chat.model.enums.ContentType;

public record SendMessageCommand(
    String connectionId,
    Long userId,
    Long channelId,
    ContentType contentType,
    String content,
    ClientMsgId clientMsgId,
    Long replyToId) {

  /** Not a reply. */
  public SendMessageCommand(
      String connectionId,
      Long userId,
      Long channelId,
      ContentType contentType,
      String content,
      ClientMsgId clientMsgId) {
    this(connectionId, userId, channelId, contentType, content, clientMsgId, null);
  }
}
