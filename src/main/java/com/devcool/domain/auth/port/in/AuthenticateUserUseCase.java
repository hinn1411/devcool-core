package com.devcool.domain.auth.port.in;

import com.devcool.domain.auth.model.TokenPair;
import com.devcool.domain.auth.port.in.command.LoginCommand;

public interface AuthenticateUserUseCase {
  TokenPair login(LoginCommand command);
}
