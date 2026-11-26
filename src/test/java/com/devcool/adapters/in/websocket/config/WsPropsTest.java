package com.devcool.adapters.in.websocket.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

/** A bad override fails startup instead of silently disabling a limit (Tomcat: ≤ 0 = off). */
class WsPropsTest {

  private static final Duration OK = Duration.ofSeconds(5);
  private static final DataSize KB = DataSize.ofKilobytes(512);

  @Test
  void validValues_areAccepted() {
    assertThatCode(() -> new WsIdleProps(Duration.ofSeconds(90))).doesNotThrowAnyException();
    assertThatCode(() -> new WsSendLimitsProps(OK, KB, OK)).doesNotThrowAnyException();
  }

  @Test
  void missingValues_areRejected() {
    assertThatThrownBy(() -> new WsIdleProps(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new WsSendLimitsProps(null, KB, OK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new WsSendLimitsProps(OK, null, OK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new WsSendLimitsProps(OK, KB, null))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void nonPositiveValues_areRejected(long value) {
    Duration duration = Duration.ofMillis(value);
    assertThatThrownBy(() -> new WsIdleProps(duration))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WsSendLimitsProps(duration, KB, OK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WsSendLimitsProps(OK, DataSize.ofBytes(value), OK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WsSendLimitsProps(OK, KB, duration))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
