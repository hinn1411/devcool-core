package com.devcool.adapters.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(name = "RefreshTokenResponse", description = "Refresh token payload")
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefreshTokenResponse {
  @Schema(description = "Unique identifier of the user", example = "101")
  private String accessToken;

  @Schema(description = "Registered username", example = "hien_giang")
  private String refreshToken;
}
