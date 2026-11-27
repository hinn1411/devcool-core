package com.devcool.adapters.in.websocket.dto.v2;

import com.devcool.domain.chat.model.ReadReceiptEvent;

/** {@code READ_RECEIPT} */
public record ReadReceiptPayload(Long userId, long seq) {

  public static ReadReceiptPayload from(ReadReceiptEvent event) {
    return new ReadReceiptPayload(event.userId(), event.seq());
  }
}
