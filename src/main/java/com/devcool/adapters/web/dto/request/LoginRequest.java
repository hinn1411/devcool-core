package com.devcool.adapters.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest (
        @NotBlank @Size(min = 8, max = 50) String username,
        @NotBlank @Size(min = 8, max = 255) String password
) { }
