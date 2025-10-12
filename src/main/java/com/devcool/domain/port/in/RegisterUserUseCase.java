package com.devcool.domain.port.in;

import com.devcool.domain.port.in.command.RegisterUserCommand;

public interface RegisterUserUseCase {
    Integer register(RegisterUserCommand cmd);
}

