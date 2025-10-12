package com.devcool.adapter.in.web.dto;

import com.devcool.domain.model.enums.Role;
import com.devcool.domain.model.enums.UserStatus;

public record UserResponse(
        Integer id,
        String username,
        String email,
        String name,
        String avatar,
        Role role,
        UserStatus status
) {}