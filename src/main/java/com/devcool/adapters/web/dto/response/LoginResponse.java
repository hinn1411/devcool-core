package com.devcool.adapters.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(name = "LoginResponse", description = "Login result payload")
@Builder
public class LoginResponse {
    @Schema(description = "Unique identifier of the user", example = "101")
    private String accessToken;

    @Schema(description = "Registered username", example = "hien_giang")
    private String refreshToken;
}