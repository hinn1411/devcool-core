package com.devcool.domain.user.exception;

import com.devcool.domain.common.DomainException;
import com.devcool.domain.common.ErrorCode;

import java.util.Map;

public class EmailAlreadyUsedException extends DomainException {
    public EmailAlreadyUsedException(String email) {
        super(ErrorCode.EMAIL_ALREADY_USED, "Email already in use",
                Map.of("email", email));
    }
}
