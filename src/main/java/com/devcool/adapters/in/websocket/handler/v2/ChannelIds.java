package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.ClientEnvelope;
import java.util.Objects;

final class ChannelIds {

  private ChannelIds() {}

  static Long require(ClientEnvelope frame) {
    if (Objects.isNull(frame.channelId())) {
      throw new InvalidFrameException("Missing channelId");
    }
    return frame.channelId();
  }
}
