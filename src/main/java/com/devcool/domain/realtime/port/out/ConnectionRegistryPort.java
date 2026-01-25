package com.devcool.domain.realtime.port.out;

import java.util.Set;

public interface ConnectionRegistryPort {
  void registerConnection(String connectionId, Integer userId);
  void removeConnection(String connectionId);
  Integer getUserId(String connectionId);
  void subscribe(String connectionId, Integer channelId);
  void unsubscribe(String connectionId, Integer channelId);
  Set<String> getConnectionsByChannel(Integer channelId);
}
