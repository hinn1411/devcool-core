package com.devcool.domain.realtime.port.out;

public interface RealtimeEmitterPort {
  void sendMessageToConnection(String connectionId, Object payload);
}
