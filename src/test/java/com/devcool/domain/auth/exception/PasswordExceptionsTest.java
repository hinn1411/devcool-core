package com.devcool.domain.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.devcool.domain.common.ErrorCode;
import org.junit.jupiter.api.Test;

class PasswordExceptionsTest {

  @Test
  void passwordIncorrect_carriesItsErrorCodeAndNoDetails() {
    PasswordIncorrectException ex = new PasswordIncorrectException();

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_INCORRECT);
    assertThat(ex.getDetails()).isEmpty();
  }

  @Test
  void passwordNotMatch_carriesItsErrorCodeAndNoDetails() {
    PasswordNotMatchException ex = new PasswordNotMatchException();

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_NOT_MATCH);
    assertThat(ex.getDetails()).isEmpty();
  }
}
