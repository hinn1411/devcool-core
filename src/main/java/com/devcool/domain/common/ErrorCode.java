package com.devcool.domain.common;

public enum ErrorCode {
    USER_NOT_FOUND("USR_404"),
    EMAIL_ALREADY_USED("USR_409"),
    USERNAME_ALREADY_USED("USR_410"),
    PASSWORD_WEAK("USR_422"),
    VALIDATION_ERROR("VLD_401"),
    INTERNAL_SERVER_ERROR("SVR_500"),
    OK("SVR_200");

    private final String code;
    ErrorCode(String code) { this.code = code; }
    public String code() { return code; }
}
