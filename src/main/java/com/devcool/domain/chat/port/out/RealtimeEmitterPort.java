package com.devcool.domain.chat.port.out;

public interface RealtimeEmitterPort {
  void sendMessageToConnection(String connectionId, Object payload);
}
