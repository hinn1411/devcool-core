package com.devcool.domain.user.port.in.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChangePasswordCommandTest {

  @Test
  void toString_showsTheUserIdAndNoPassword() {
    ChangePasswordCommand command =
        new ChangePasswordCommand(7, "old-secret", "new-secret", "confirm-secret");

    assertThat(command.toString())
        .contains("7")
        .doesNotContain("old-secret", "new-secret", "confirm-secret");
  }
}
