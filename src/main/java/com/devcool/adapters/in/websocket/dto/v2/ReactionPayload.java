package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.ReactionEvent;

/** {@code REACTION}; the channel id is on the envelope. */
public record ReactionPayload(Long messageId, String emoji, Long userId, boolean on) {

  public static ReactionPayload from(ReactionEvent event) {
    return new ReactionPayload(event.messageId(), event.emoji(), event.userId(), event.on());
  }
}
