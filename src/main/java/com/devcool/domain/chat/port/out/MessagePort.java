package com.devcool.domain.chat.port.out;

import com.devcool.domain.chat.model.Message;
import com.devcool.domain.chat.model.MessageItem;
import java.util.List;

public interface MessagePort {
  Long save(Message message);

  /** Returns up to {@code size} messages older than {@code cursorId}, newest first. */
  List<MessageItem> findMessages(Long channelId, Long cursorId, Integer size);
}
