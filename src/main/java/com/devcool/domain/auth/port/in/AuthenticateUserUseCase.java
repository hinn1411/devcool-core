package com.devcool.domain.auth.port.in;

import com.devcool.domain.auth.port.in.command.LoginCommand;
import com.devcool.domain.auth.model.TokenPair;

public interface AuthenticateUserUseCase {
  TokenPair login(LoginCommand command);
}
