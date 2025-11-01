package com.devcool.domain.common;

public enum ErrorCode {
    USER_NOT_FOUND("USR_404"),
    EMAIL_ALREADY_USED("USR_409"),
    USERNAME_ALREADY_USED("USR_410"),
    PASSWORD_WEAK("USR_422"),
    PASSWORD_INCORRECT("USR_223"),
    PASSWORD_NOT_MATCH("USR_224"),
    PASSWORD_DUPLICATE("USR_225"),
    VALIDATION_ERROR("VLD_401"),
    INTERNAL_SERVER_ERROR("SVR_500"),
    FORBIDDEN("SVR_403"),
    OK("SVR_200"),
    CREATED("SVR_201");

    private final String code;
    ErrorCode(String code) { this.code = code; }
    public String code() { return code; }
}
