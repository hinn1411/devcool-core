package com.devcool.adapter.in.web.dto;

import com.devcool.domain.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(min=3, max=20) String username,
        @NotBlank @Size(min=8, max=255) String password,
        @Email @NotBlank String email,
        @NotBlank String name,
        Role role
) {}