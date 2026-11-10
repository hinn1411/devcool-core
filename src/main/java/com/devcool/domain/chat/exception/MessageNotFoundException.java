package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

/** Unknown, deleted, or in a channel the caller is not in: one answer, so ids can't be probed. */
public class MessageNotFoundException extends DomainException {
  public MessageNotFoundException(Long messageId) {
    super(ErrorCode.MESSAGE_NOT_FOUND, "Message does not exist", Map.of("messageId", messageId));
  }
}
