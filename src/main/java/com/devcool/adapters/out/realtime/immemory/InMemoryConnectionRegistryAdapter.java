package com.devcool.adapters.out.realtime.immemory;

import com.devcool.domain.realtime.port.out.ConnectionRegistryPort;

import java.util.Set;

public class InMemoryConnectionRegistryAdapter implements ConnectionRegistryPort {
  @Override
  public void registerConnection(String connectionId, Integer userId) {

  }

  @Override
  public void removeConnection(String connectionId) {

  }

  @Override
  public Integer getUserId(String connection) {
    return 0;
  }

  @Override
  public void subscribe(String connectionId, Integer channelId) {

  }

  @Override
  public void unsubscribe(String connectionId, Integer channelId) {

  }

  @Override
  public Set<String> getConnectionsByChannel(Integer channelId) {
    return Set.of();
  }
}
