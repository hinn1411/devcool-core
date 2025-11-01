package com.devcool.adapters.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(name = "RegisterUserResponse", description = "User data returned after successful registration")
@Builder
public class RegisterUserResponse {
    @Schema(description = "User id of the authenticated user", example = "123")
    private Integer userId;
}
