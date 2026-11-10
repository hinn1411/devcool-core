package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** The client edited an outdated version: reload and retry. */
public class MessageVersionConflictException extends DomainException {
  public MessageVersionConflictException() {
    super(ErrorCode.MESSAGE_VERSION_CONFLICT, "Message was changed; reload and retry", null);
  }
}
