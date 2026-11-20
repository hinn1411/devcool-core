package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.MessageEvent;

/** Best-effort push to one live connection (at-most-once, ADR-0006): a miss is caught up by seq. */
public interface RealtimeEmitterPort {
  void send(String connectionId, MessageEvent event);
}
