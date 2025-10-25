package com.devcool.domain.user.port.in.command;

public record RegisterUserCommand(
        String username,
        String rawPassword,
        String email,
        String name
) {}