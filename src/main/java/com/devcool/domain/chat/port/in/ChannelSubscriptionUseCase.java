package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;

/** Which channels' live events a connection receives. */
public interface ChannelSubscriptionUseCase {
  /** Members only. */
  void subscribe(SubscribeCommand command);

  /** No membership check: it only shrinks this connection's own subscriptions. */
  void unsubscribe(UnsubscribeCommand command);
}
