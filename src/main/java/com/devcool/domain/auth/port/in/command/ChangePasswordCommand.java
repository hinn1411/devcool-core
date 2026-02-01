package com.devcool.domain.auth.port.in.command;

public record ChangePasswordCommand(
    String oldPassword, String newPassword, String confirmedPassword) {}
