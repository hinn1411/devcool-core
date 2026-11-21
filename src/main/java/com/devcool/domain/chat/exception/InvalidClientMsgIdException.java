package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

public class InvalidClientMsgIdException extends DomainException {
  public InvalidClientMsgIdException() {
    super(ErrorCode.INVALID_CLIENT_MSG_ID, "clientMsgId must be a canonical UUID", null);
  }
}
