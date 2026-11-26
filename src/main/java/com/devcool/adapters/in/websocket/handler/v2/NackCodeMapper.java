package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.NackCode;
import com.devcool.domain.common.ErrorCode;

/** The WebSocket twin of {@code HttpErrorMapper}. */
public final class NackCodeMapper {

  private NackCodeMapper() {}

  public static NackCode toNackCode(ErrorCode code) {
    throw new UnsupportedOperationException();
  }
}
