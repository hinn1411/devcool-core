package com.devcool.domain.user.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class UsernameAlreadyUsedException extends DomainException {
    public UsernameAlreadyUsedException(String username) {
        super(ErrorCode.USERNAME_ALREADY_USED, "Username already in used",
                Map.of("username", username));
    }
}
