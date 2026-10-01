package com.devcool.adapters.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(
    name = "UserProfileResponse",
    description = "Public profile of a user, visible to any authenticated user")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
  @Schema(description = "Unique identifier of the user", example = "101")
  private String id;

  @Schema(description = "Registered username", example = "hien_giang")
  private String username;

  @Schema(description = "User's full name", example = "Hien Giang")
  private String name;

  @Schema(
      description = "URL or path to the user avatar",
      example = "https://cdn.devcool.com/avatars/hien.png")
  private String avatar;
}
