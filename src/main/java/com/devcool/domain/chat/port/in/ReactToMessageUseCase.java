package com.devcool.domain.chat.port.in;

import com.devcool.domain.chat.port.in.command.ReactionCommand;

public interface ReactToMessageUseCase {
  /** Adds the caller's reaction; repeating it is a no-op. */
  void react(ReactionCommand command);

  /** Removes the caller's reaction; removing an absent one is a no-op. */
  void unreact(ReactionCommand command);
}
