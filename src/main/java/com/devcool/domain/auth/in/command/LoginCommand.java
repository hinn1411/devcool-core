package com.devcool.domain.auth.in.command;

public record LoginCommand (
        String username,
        String password
) { }
