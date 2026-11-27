package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.EditedMessage;
import java.time.Instant;

/** {@code ACK} of an {@code EDIT}. */
public record EditAckPayload(Long messageId, int version, Instant editedTime) {

  public static EditAckPayload from(EditedMessage message) {
    return new EditAckPayload(message.id(), message.version(), message.editedTime());
  }
}
