package com.devcool.application.service.chat;

import com.devcool.domain.chat.model.RealtimeEvent;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fans an event out to the channel's local subscribers once the current transaction commits (never
 * before, ADR-0005); right away when none is active. Moves behind the backplane in P4-T07.
 */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ChannelEventPublisher {

  private final ConnectionRegistryPort connectionRegistryPort;
  private final RealtimeEmitterPort realtimeEmitterPort;

  /** {@code originConnectionId} (the actor's socket, null over REST) gets no copy. */
  public void publishAfterCommit(RealtimeEvent event, String originConnectionId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      publish(event, originConnectionId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publish(event, originConnectionId);
          }
        });
  }

  private void publish(RealtimeEvent event, String originConnectionId) {
    for (String connectionId : connectionRegistryPort.getConnectionsByChannel(event.channelId())) {
      if (!Objects.equals(originConnectionId, connectionId)) {
        realtimeEmitterPort.send(connectionId, event);
      }
    }
  }
}
