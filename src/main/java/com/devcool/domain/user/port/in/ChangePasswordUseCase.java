package com.devcool.domain.user.port.in;

import com.devcool.domain.user.port.in.command.ChangePasswordCommand;

public interface ChangePasswordUseCase {
  void change(ChangePasswordCommand command);
}
