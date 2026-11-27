package com.devcool.application.service.chat;

import com.devcool.domain.chat.model.RealtimeEvent;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Pushes to the channel's local subscribers after commit, or at once outside a transaction. */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring injects shared singleton beans; they are not owned state")
public class ChannelEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(ChannelEventPublisher.class);

  private final ConnectionRegistryPort connectionRegistryPort;
  private final RealtimeEmitterPort realtimeEmitterPort;

  /** Skips {@code originConnectionId} (null over REST). */
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

  /** Never throws: the change is committed, and events are at-most-once (ADR-0006). */
  private void publish(RealtimeEvent event, String originConnectionId) {
    try {
      for (String connectionId :
          connectionRegistryPort.getConnectionsByChannel(event.channelId())) {
        if (!Objects.equals(originConnectionId, connectionId)) {
          realtimeEmitterPort.send(connectionId, event);
        }
      }
    } catch (RuntimeException ex) {
      log.warn(
          "Publish of {} in channel {} failed",
          event.getClass().getSimpleName(),
          event.channelId(),
          ex);
    }
  }
}
