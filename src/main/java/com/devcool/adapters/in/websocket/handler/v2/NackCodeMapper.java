package com.devcool.adapters.in.websocket.handler.v2;

import com.devcool.adapters.in.websocket.dto.v2.NackCode;
import com.devcool.domain.common.ErrorCode;

/** The WebSocket twin of {@code HttpErrorMapper}: same buckets, one client reaction each. */
public final class NackCodeMapper {

  private NackCodeMapper() {}

  public static NackCode toNackCode(ErrorCode code) {
    return switch (code) {
      case USER_NOT_FOUND, CHANNEL_NOT_FOUND, MESSAGE_NOT_FOUND -> NackCode.NOT_FOUND;
      case EMAIL_ALREADY_USED,
              USERNAME_ALREADY_USED,
              DUPLICATE_MEMBER,
              CLIENT_MSG_ID_REUSED,
              MESSAGE_VERSION_CONFLICT,
              REACTION_LIMIT_REACHED ->
          NackCode.CONFLICT;
      case PASSWORD_WEAK,
              PASSWORD_INCORRECT,
              PASSWORD_NOT_MATCH,
              PASSWORD_DUPLICATE,
              USER_DUPLICATE,
              VALIDATION_ERROR,
              INVALID_CHANNEL_CONFIG,
              INVALID_MEDIA_CONTENT,
              INVALID_MESSAGE_CONFIG,
              INVALID_CLIENT_MSG_ID,
              INVALID_HISTORY_CURSOR,
              INVALID_CHANNEL_CURSOR,
              INVALID_EMOJI,
              INVALID_SEARCH_QUERY,
              INVALID_OBJECT_KEY,
              UNSUPPORTED_MEDIA_TYPE,
              TOO_LARGE_MEDIA ->
          NackCode.INVALID;
        // The socket was authenticated at the handshake; a later auth error is a denial.
      case REFRESH_TOKEN_INVALID,
              INVALID_CREDENTIALS,
              UNAUTHENTICATED,
              MEMBER_NOT_FOUND,
              FORBIDDEN,
              MESSAGE_EDIT_WINDOW_CLOSED ->
          NackCode.FORBIDDEN;
      case OK, CREATED, INTERNAL_SERVER_ERROR -> NackCode.INTERNAL;
    };
  }
}
