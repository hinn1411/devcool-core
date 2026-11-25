package com.devcool.adapters.in.websocket.dto;

import com.devcool.domain.chat.model.enums.ContentType;

public record ClientFrame(
    FrameType action,
    Long channelId,
    ContentType contentType,
    String content,
    String clientMsgId, // Idempotent key
    Long replyToId) {

  /** Not a reply. */
  public ClientFrame(
      FrameType action,
      Long channelId,
      ContentType contentType,
      String content,
      String clientMsgId) {
    this(action, channelId, contentType, content, clientMsgId, null);
  }
}
