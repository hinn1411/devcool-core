package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/**
 * The insert hit the sender's {@code clientMsgId} unique key: a concurrent send with the same key
 * committed first. The transaction that sees this is aborted, so the winner is re-read in a new
 * one.
 */
public class DuplicateClientMsgIdException extends DomainException {
  public DuplicateClientMsgIdException() {
    super(ErrorCode.DUPLICATE_CLIENT_MSG_ID, "Message with this clientMsgId already exists", null);
  }
}
