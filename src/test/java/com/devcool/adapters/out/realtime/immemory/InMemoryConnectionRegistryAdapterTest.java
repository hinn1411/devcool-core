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
    // Only live connections can subscribe (P4-T01).
    for (String c : List.of("c", "c1", "c2", "c3")) {
      registry.registerConnection(c, 100L);
    }
  }

  @Nested
  class SubscribeAfterCleanup {
    @Test
    void unregisteredConnection_isNotSubscribed() {
      registry.subscribe("ghost", 1L);

      assertThat(registry.getConnectionsByChannel(1L)).isEmpty();
    }

    @Test
    void removedConnection_isNotSubscribedAgain() {
      registry.removeConnection("c1");

      registry.subscribe("c1", 1L);

      assertThat(registry.getConnectionsByChannel(1L)).isEmpty();
    }
  }

  @Nested
  class ConnectionRegistration {
    String connectionId = "c1";
    Long userId = 100L;

    @Test
    void registeredConnection_getUserIdReturnsUser() {

      registry.registerConnection(connectionId, userId);
      Long storedUserId = registry.getUserId(connectionId);
      assertThat(storedUserId).isEqualTo(userId);
    }

    @Test
    void removeConnection_removesItFromEveryChannel() {
      registry.subscribe("c1", 1L);
      registry.subscribe("c2", 1L);
      registry.subscribe("c1", 2L);

      registry.removeConnection("c1");

      assertThat(registry.getConnectionsByChannel(1L)).containsExactly("c2");
      assertThat(registry.getConnectionsByChannel(2L)).isEmpty();
    }

    @Test
    void removedConnection_getUserIdReturnsNull() {
      registry.registerConnection(connectionId, userId);
      registry.removeConnection(connectionId);
      Long actual = registry.getUserId(connectionId);
      assertThat(actual).isNull();
    }
  }

  @Nested
  class ConnectionSubscription {
    @Test
    void multipleConnectionsForAChannel_returnsConnections() {
      List<String> connectionIds = List.of("c1", "c2", "c3");
      Long channelId = 100L;
      for (String connectionId : connectionIds) {
        registry.subscribe(connectionId, channelId);
      }

      Set<String> actual = registry.getConnectionsByChannel(channelId);

      assertThat(actual).hasSize(connectionIds.size()).containsExactlyInAnyOrder("c1", "c2", "c3");
    }

    @Test
    void oneConnectionForMultipleChannel_returnsOnlyConnection() {
      String connectionId = "c";
      List<Long> channelIds = List.of(100L, 101L, 102L);
      for (Long channelId : channelIds) {
        registry.subscribe(connectionId, channelId);
      }

      for (Long channelId : channelIds) {
        Set<String> actual = registry.getConnectionsByChannel(channelId);
        assertThat(actual).containsExactly(connectionId);
      }
    }

    @Test
    void duplicateSubscription_returnsOnlyConnection() {
      String connectionId = "c";
      Long channelId = 100L;
      registry.subscribe(connectionId, channelId);
      registry.subscribe(connectionId, channelId);

      Set<String> actual = registry.getConnectionsByChannel(channelId);

      assertThat(actual).containsExactly(connectionId);
    }

    @Test
    void emptyChannel_returnsEmptySet() {
      Long emptyChannelId = 1L;
      Set<String> actual = registry.getConnectionsByChannel(emptyChannelId);

      assertThat(actual).isNotNull().isEmpty();
    }
  }

  @Nested
  class ConnectionUnsubscription {
    @Test
    void currentConnection_anotherConnectionStillPersist() {
      String connectionId = "c";
      Long currentChannelId = 100L;
      Long anotherChannelId = 101L;
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
      Long channelId = 100L;

      assertThatCode(() -> registry.unsubscribe(connectionId, channelId))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class UserUnsubscription {
    @Test
    void removesAllOfThatUsersConnectionsOnly() {
      registry.registerConnection("bob-tab1", 9L);
      registry.registerConnection("bob-tab2", 9L);
      registry.registerConnection("alice", 7L);
      for (String c : new String[] {"bob-tab1", "bob-tab2", "alice"}) {
        registry.subscribe(c, 42L);
      }
      registry.subscribe("bob-tab1", 43L);

      registry.unsubscribeUser(9L, 42L);

      assertThat(registry.getConnectionsByChannel(42L)).containsExactly("alice");
      assertThat(registry.getConnectionsByChannel(43L)).containsExactly("bob-tab1");
      assertThat(registry.getUserId("bob-tab1")).isEqualTo(9L);
    }

    @Test
    void userWithNoConnections_noThrows() {
      assertThatCode(() -> registry.unsubscribeUser(9L, 42L)).doesNotThrowAnyException();
    }
  }
}
