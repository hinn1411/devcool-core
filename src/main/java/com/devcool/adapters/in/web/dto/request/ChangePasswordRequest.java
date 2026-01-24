package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest", description = "Details required to change user password")
public record ChangePasswordRequest(
    @NotBlank @Size(min = 8, max = 255) String currentPassword,
    @NotBlank @Size(min = 8, max = 255) String newPassword,
    @NotBlank @Size(min = 8, max = 255) String confirmedPassword) {}
