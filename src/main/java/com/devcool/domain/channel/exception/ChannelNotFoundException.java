package com.devcool.domain.channel.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class ChannelNotFoundException extends DomainException {
  public ChannelNotFoundException(Integer channelId) {
    super(ErrorCode.CHANNEL_NOT_FOUND, "Channel does not exist", Map.of("channelId", channelId));
  }
}
