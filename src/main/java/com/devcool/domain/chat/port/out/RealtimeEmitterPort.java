package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.MessageEvent;

/** At-most-once push (ADR-0006). */
public interface RealtimeEmitterPort {
  void send(String connectionId, MessageEvent event);
}
