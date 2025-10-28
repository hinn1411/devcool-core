package com.devcool.domain.auth.in;

import com.devcool.domain.auth.in.command.LoginCommand;
import com.devcool.domain.auth.model.TokenPair;

public interface AuthenticateUserUseCase {
    TokenPair login(LoginCommand command);
}
