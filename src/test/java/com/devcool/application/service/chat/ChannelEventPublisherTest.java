package com.devcool.application.service.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devcool.domain.chat.model.MessageDeletedEvent;
import com.devcool.domain.chat.model.RealtimeEvent;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** P4-T04: commit → publish; the actor's socket is skipped; a rollback publishes nothing. */
@ExtendWith(MockitoExtension.class)
class ChannelEventPublisherTest {

  private static final RealtimeEvent EVENT =
      new MessageDeletedEvent(42L, 500L, 9L, Instant.parse("2026-11-26T10:00:00Z"));

  @Mock private ConnectionRegistryPort registry;
  @Mock private RealtimeEmitterPort emitter;

  private ChannelEventPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new ChannelEventPublisher(registry, emitter);
  }

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void noTransaction_sendsRightAway_toEveryChannelConnectionButTheActors() {
    when(registry.getConnectionsByChannel(42L)).thenReturn(Set.of("a", "b", "c"));

    publisher.publishAfterCommit(EVENT, "a");

    verify(emitter).send("b", EVENT);
    verify(emitter).send("c", EVENT);
    verify(emitter, never()).send(eq("a"), any());
  }

  @Test
  void restCall_withNoActorSocket_sendsToEveryConnection() {
    when(registry.getConnectionsByChannel(42L)).thenReturn(Set.of("a", "b"));

    publisher.publishAfterCommit(EVENT, null);

    verify(emitter).send("a", EVENT);
    verify(emitter).send("b", EVENT);
  }

  @Test
  void insideATransaction_sendsNothingUntilItCommits() {
    TransactionSynchronizationManager.initSynchronization();
    publisher.publishAfterCommit(EVENT, "a");

    verifyNoInteractions(registry, emitter);

    when(registry.getConnectionsByChannel(42L)).thenReturn(Set.of("a", "b"));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);

    verify(emitter).send("b", EVENT);
    verify(emitter, never()).send(eq("a"), any());
  }

  @Test
  void rolledBackTransaction_sendsNothing() {
    TransactionSynchronizationManager.initSynchronization();
    publisher.publishAfterCommit(EVENT, "a");

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    verifyNoInteractions(registry, emitter);
  }
}
