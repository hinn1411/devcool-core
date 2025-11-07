package com.devcool.adapters.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank @Size(min = 8, max = 255) String currentPassword,
    @NotBlank @Size(min = 8, max = 255) String newPassword,
    @NotBlank @Size(min = 8, max = 255) String confirmedPassword) {}
