package com.devcool.domain.user.port.in;

import com.devcool.domain.user.port.in.command.RegisterUserCommand;

public interface RegisterUserUseCase {
  Long register(RegisterUserCommand command);
}
