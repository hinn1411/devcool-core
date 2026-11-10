package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.EditableMessage;
import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import com.devcool.domain.chat.model.MessageKey;
import java.time.Instant;
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

  /** Up to {@code size} messages older than {@code cursorId}, newest first (deprecated alias). */
  List<MessageItem> findMessages(Long channelId, Long cursorId, Integer size);

  /** Up to {@code size} messages with seq below {@code beforeSeq} (null = newest), seq DESC. */
  List<MessageItem> findBeforeSeq(Long channelId, Long beforeSeq, Integer size);

  /** Up to {@code size} messages with seq above {@code afterSeq}, seq ASC. */
  List<MessageItem> findAfterSeq(Long channelId, Long afterSeq, Integer size);

  /** The message unless it is unknown or deleted. */
  Optional<EditableMessage> findForEdit(Long messageId);

  /**
   * Replaces the content and bumps the version, only if the stored version is still {@code
   * expectedVersion} and the message is not deleted. False if no row matched (conflict).
   */
  boolean updateContent(Long messageId, int expectedVersion, String content, Instant editedTime);
}
