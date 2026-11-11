package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

public class MessageEditWindowClosedException extends DomainException {
  public MessageEditWindowClosedException() {
    super(ErrorCode.MESSAGE_EDIT_WINDOW_CLOSED, "Messages can only be edited for 24 hours", null);
  }
}
