package com.devcool.adapters.out.realtime.immemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InMemoryConnectionRegistryAdapterTest {
  private InMemoryConnectionRegistryAdapter registry;

  @BeforeEach
  void setUp() {
    registry = new InMemoryConnectionRegistryAdapter();
  }

  @Nested
  class ConnectionRegistration {
    String connectionId = "c1";
    Integer userId = 100;

    @Test
    void registeredConnection_getUserIdReturnsUser() {

      registry.registerConnection(connectionId, userId);
      Integer storedUserId = registry.getUserId(connectionId);
      assertThat(storedUserId).isEqualTo(userId);
    }

    @Test
    void removeConnection_removesItFromEveryChannel() {
      registry.subscribe("c1", 1);
      registry.subscribe("c2", 1);
      registry.subscribe("c1", 2);

      registry.removeConnection("c1");

      assertThat(registry.getConnectionsByChannel(1)).containsExactly("c2");
      assertThat(registry.getConnectionsByChannel(2)).isEmpty();
    }

    @Test
    void removedConnection_getUserIdReturnsNull() {
      registry.registerConnection(connectionId, userId);
      registry.removeConnection(connectionId);
      Integer actual = registry.getUserId(connectionId);
      assertThat(actual).isNull();
    }
  }

  @Nested
  class ConnectionSubscription {
    @Test
    void multipleConnectionsForAChannel_returnsConnections() {
      List<String> connectionIds = List.of("c1", "c2", "c3");
      Integer channelId = 100;
      for (String connectionId : connectionIds) {
        registry.subscribe(connectionId, channelId);
      }

      Set<String> actual = registry.getConnectionsByChannel(channelId);

      assertThat(actual).hasSize(connectionIds.size()).containsExactlyInAnyOrder("c1", "c2", "c3");
    }

    @Test
    void oneConnectionForMultipleChannel_returnsOnlyConnection() {
      String connectionId = "c";
      List<Integer> channelIds = List.of(100, 101, 102);
      for (Integer channelId : channelIds) {
        registry.subscribe(connectionId, channelId);
      }

      for (Integer channelId : channelIds) {
        Set<String> actual = registry.getConnectionsByChannel(channelId);
        assertThat(actual).containsExactly(connectionId);
      }
    }

    @Test
    void duplicateSubscription_returnsOnlyConnection() {
      String connectionId = "c";
      Integer channelId = 100;
      registry.subscribe(connectionId, channelId);
      registry.subscribe(connectionId, channelId);

      Set<String> actual = registry.getConnectionsByChannel(channelId);

      assertThat(actual).containsExactly(connectionId);
    }

    @Test
    void emptyChannel_returnsEmptySet() {
      Integer emptyChannelId = 1;
      Set<String> actual = registry.getConnectionsByChannel(emptyChannelId);

      assertThat(actual).isNotNull().isEmpty();
    }
  }

  @Nested
  class ConnectionUnsubscription {
    @Test
    void currentConnection_anotherConnectionStillPersist() {
      String connectionId = "c";
      Integer currentChannelId = 100;
      Integer anotherChannelId = 101;
      registry.subscribe(connectionId, currentChannelId);
      registry.subscribe(connectionId, anotherChannelId);
      registry.unsubscribe(connectionId, currentChannelId);

      Set<String> actual = registry.getConnectionsByChannel(anotherChannelId);

      assertThat(registry.getConnectionsByChannel(currentChannelId)).isEmpty();
      assertThat(actual).hasSize(1).contains(connectionId);
    }

    @Test
    void unexistedConnection_noThrows() {
      String connectionId = "c";
      Integer channelId = 100;

      assertThatCode(() -> registry.unsubscribe(connectionId, channelId))
          .doesNotThrowAnyException();
    }
  }
}
