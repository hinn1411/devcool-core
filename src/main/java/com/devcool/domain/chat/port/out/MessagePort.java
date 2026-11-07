package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessagePort {
  /**
   * Inserts; empty if the sender's {@code clientMsgId} is taken (the transaction is then aborted).
   */
  Optional<Long> save(Message message);

  /** The id, channel, seq and time of the sender's message stored under this idempotency key. */
  Optional<MessageKey> findByClientMsgId(Long senderId, UUID clientMsgId);

  /** Returns up to {@code size} messages older than {@code cursorId}, newest first. */
  List<MessageItem> findMessages(Long channelId, Long cursorId, Integer size);
}
