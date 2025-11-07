package com.devcool.adapters.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterUserRequest", description = "Details required to register a new user")
public record RegisterUserRequest(
    @Schema(description = "Desired username", example = "hien_giang", minLength = 8, maxLength = 50)
        @NotBlank
        @Size(min = 8, max = 50)
        String username,
    @Schema(description = "Password", example = "Secret#123", minLength = 8, maxLength = 255)
        @NotBlank
        @Size(min = 8, max = 255)
        String password,
    @Schema(description = "Email address", example = "hien@example.com") @Email @NotBlank
        String email,
    @Schema(description = "Full name", example = "Hien Giang") @NotBlank String name) {}
