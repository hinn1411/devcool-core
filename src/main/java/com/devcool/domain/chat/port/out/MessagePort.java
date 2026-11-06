package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessagePort {
  /**
   * Inserts the message and returns its id.
   *
   * @throws com.devcool.domain.chat.exception.DuplicateClientMsgIdException when the sender already
   *     has a message with this {@code clientMsgId}
   */
  Long save(Message message);

  /** The sender's message carrying this idempotency key, with its id, channel, seq and time. */
  Optional<Message> findByClientMsgId(Long senderId, UUID clientMsgId);

  /** Returns up to {@code size} messages older than {@code cursorId}, newest first. */
  List<MessageItem> findMessages(Long channelId, Long cursorId, Integer size);
}
