package com.devcool.domain.channel.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** Malformed channel-list cursor. */
public class InvalidChannelCursorException extends DomainException {
  public InvalidChannelCursorException() {
    super(ErrorCode.INVALID_CHANNEL_CURSOR, "Invalid channel cursor", null);
  }
}
