package com.devcool.domain.port.in.command;

import com.devcool.domain.model.enums.Role;

public record RegisterUserCommand(
        String username,
        String rawPassword,
        String email,
        String name,
        Role role
) {}