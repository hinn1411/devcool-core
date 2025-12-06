package com.devcool.domain.channel.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

public class InvalidChannelConfigException extends DomainException {
  public InvalidChannelConfigException(String message) {
    super(ErrorCode.INVALID_CHANNEL_CONFIG, message, null);
  }
}
