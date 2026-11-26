package com.devcool.adapters.in.websocket.handler.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.adapters.in.websocket.dto.v2.NackCode;
import com.devcool.domain.common.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class NackCodeMapperTest {

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyErrorCodeHasANackCode(ErrorCode code) {
    assertThat(NackCodeMapper.toNackCode(code)).isNotNull();
  }

  /** Same buckets as HttpErrorMapper: 403, 404, 409, 400/422. */
  @ParameterizedTest
  @CsvSource({
    "MEMBER_NOT_FOUND, FORBIDDEN",
    "FORBIDDEN, FORBIDDEN",
    "MESSAGE_EDIT_WINDOW_CLOSED, FORBIDDEN",
    "CHANNEL_NOT_FOUND, NOT_FOUND",
    "MESSAGE_NOT_FOUND, NOT_FOUND",
    "CLIENT_MSG_ID_REUSED, CONFLICT",
    "MESSAGE_VERSION_CONFLICT, CONFLICT",
    "INVALID_CLIENT_MSG_ID, INVALID",
    "INVALID_MESSAGE_CONFIG, INVALID",
    "VALIDATION_ERROR, INVALID",
    "INTERNAL_SERVER_ERROR, INTERNAL"
  })
  void mapsToTheClientReaction(ErrorCode code, NackCode expected) {
    assertThat(NackCodeMapper.toNackCode(code)).isEqualTo(expected);
  }
}
