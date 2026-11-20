package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** {@code clientMsgId} is missing or not a canonical UUID. The raw value is never echoed. */
public class InvalidClientMsgIdException extends DomainException {
  public InvalidClientMsgIdException() {
    super(ErrorCode.INVALID_CLIENT_MSG_ID, "clientMsgId must be a canonical UUID", null);
  }
}
