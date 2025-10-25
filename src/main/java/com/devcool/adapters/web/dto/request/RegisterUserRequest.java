package com.devcool.adapters.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(min=8, max=50) String username,
        @NotBlank @Size(min=8, max=255) String password,
        @Email @NotBlank String email,
        @NotBlank String name
) {}