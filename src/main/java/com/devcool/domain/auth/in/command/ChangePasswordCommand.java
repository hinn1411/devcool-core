package com.devcool.domain.auth.in.command;

public record ChangePasswordCommand (
        String oldPassword,
        String newPassword,
        String confirmedPassword
) { }
