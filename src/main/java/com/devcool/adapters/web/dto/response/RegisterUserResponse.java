package com.devcool.adapters.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(name = "RegisterUserResponse", description = "User data returned after successful registration")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterUserResponse {
    @Schema(description = "User id of the authenticated user", example = "123")
    private Integer userId;
}
