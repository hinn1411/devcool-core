package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.SubscribeCommand;
import com.devcool.domain.chat.port.in.command.UnsubscribeCommand;

public interface ChannelSubscriptionUseCase {
  /** Members only. */
  void subscribe(SubscribeCommand command);

  /** No membership check: only shrinks this connection's subscriptions. */
  void unsubscribe(UnsubscribeCommand command);
}
