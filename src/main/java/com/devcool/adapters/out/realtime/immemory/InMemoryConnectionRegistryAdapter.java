package com.devcool.adapters.out.realtime.immemory;

import com.devcool.domain.realtime.port.out.ConnectionRegistryPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    connectionToUser.remove(connectionId);
  }

  @Override
  public Integer getUserId(String connectionId) {
    return connectionToUser.get(connectionId);
  }

  @Override
  public void subscribe(String connectionId, Integer channelId) {
    channelToConnections.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet())
        .add(connectionId);
  }

  @Override
  public void unsubscribe(String connectionId, Integer channelId) {
    Set<String> connectionSet = channelToConnections.get(channelId);
    if (!connectionSet.isEmpty()) {
      connectionSet.remove(connectionId);
    }
  }

  @Override
  public Set<String> getConnectionsByChannel(Integer channelId) {
    return channelToConnections.get(channelId);
  }
}
