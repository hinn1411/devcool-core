package com.devcool.domain.chat.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

/** The sender already used this {@code clientMsgId} for a message in another channel. */
public class ClientMsgIdReusedException extends DomainException {
  public ClientMsgIdReusedException() {
    super(ErrorCode.CLIENT_MSG_ID_REUSED, "clientMsgId already used in another channel", null);
  }
}
