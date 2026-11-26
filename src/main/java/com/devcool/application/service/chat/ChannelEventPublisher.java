package com.devcool.application.service.chat;

import com.devcool.domain.chat.model.RealtimeEvent;
import com.devcool.domain.chat.port.out.ConnectionRegistryPort;
import com.devcool.domain.chat.port.out.RealtimeEmitterPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    throw new UnsupportedOperationException("P4-T04 Red");
  }
}
