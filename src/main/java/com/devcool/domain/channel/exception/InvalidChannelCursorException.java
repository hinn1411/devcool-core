package com.devcool.domain.channel.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** A channel-list cursor this server did not issue (malformed or tampered). */
public class InvalidChannelCursorException extends DomainException {
  public InvalidChannelCursorException() {
    super(ErrorCode.INVALID_CHANNEL_CURSOR, "Invalid channel cursor", null);
  }
}
