package com.devcool.domain.chat.port.out;

import java.util.Set;

public interface ConnectionRegistryPort {
  void registerConnection(String connectionId, Long userId);

  void removeConnection(String connectionId);

  Long getUserId(String connectionId);

  void subscribe(String connectionId, Long channelId);

  void unsubscribe(String connectionId, Long channelId);

  /** Drops every connection of {@code userId} from the channel (removed or left). */
  void unsubscribeUser(Long userId, Long channelId);

  Set<String> getConnectionsByChannel(Long channelId);
}
