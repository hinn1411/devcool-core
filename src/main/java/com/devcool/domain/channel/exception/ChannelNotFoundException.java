package com.devcool.domain.channel.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

public class ChannelNotFoundException extends DomainException {
  public ChannelNotFoundException(String message) {
    super(ErrorCode.CHANNEL_NOT_FOUND, message, null);
  }
}
