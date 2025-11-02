package com.devcool.adapters.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credentials for authenticating a user")
public record LoginRequest(
    @Schema(description = "Unique username", example = "hien_giang", minLength = 8, maxLength = 50)
        @NotBlank
        @Size(min = 8, max = 50)
        String username,
    @Schema(
            description = "Account password",
            example = "Secret#123",
            minLength = 8,
            maxLength = 255)
        @NotBlank
        @Size(min = 8, max = 255)
        String password) {}
