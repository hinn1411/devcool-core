package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;
import java.util.Map;

/** Unknown, deleted, or not the caller's channel: one answer, no id probing. */
public class MessageNotFoundException extends DomainException {
  public MessageNotFoundException(Long messageId) {
    super(ErrorCode.MESSAGE_NOT_FOUND, "Message does not exist", Map.of("messageId", messageId));
  }
}
