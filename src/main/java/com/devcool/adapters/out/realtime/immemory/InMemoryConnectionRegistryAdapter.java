package com.devcool.adapters.out.realtime.immemory;

import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryConnectionRegistryAdapter implements ConnectionRegistryPort {
  private final Map<String, Integer> connectionToUser = new ConcurrentHashMap<>();
  private final Map<Integer, Set<String>> channelToConnections = new ConcurrentHashMap<>();

  @Override
  public void registerConnection(String connectionId, Integer userId) {
    connectionToUser.put(connectionId, userId);
  }

  @Override
  public void removeConnection(String connectionId) {
    if (connectionId == null) {
      return;
    }
    connectionToUser.remove(connectionId);
    for (Set<String> connections : channelToConnections.values()) {
      connections.remove(connectionId);
    }
  }

  @Override
  public Integer getUserId(String connectionId) {
    return connectionToUser.get(connectionId);
  }

  @Override
  public void subscribe(String connectionId, Integer channelId) {
    channelToConnections
        .computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet())
        .add(connectionId);
  }

  @Override
  public void unsubscribe(String connectionId, Integer channelId) {
    Set<String> connections = channelToConnections.get(channelId);
    if (connections != null) {
      connections.remove(connectionId);
    }
  }

  @Override
  public Set<String> getConnectionsByChannel(Integer channelId) {
    return Set.copyOf(channelToConnections.getOrDefault(channelId, Set.of()));
  }
}
