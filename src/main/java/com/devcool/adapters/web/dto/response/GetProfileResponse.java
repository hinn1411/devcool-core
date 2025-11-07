package com.devcool.adapters.web.dto.response;

import com.devcool.domain.user.model.enums.Role;
import com.devcool.domain.user.model.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.*;

@Schema(name = "GetProfileResponse", description = "Get profile result payload")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetProfileResponse {
  @Schema(description = "Unique identifier of the user", example = "101")
  private String id;

  @Schema(description = "Registered username", example = "hien_giang")
  private String username;

  @Schema(description = "User's email address", example = "hien@example.com")
  private String email;

  @Schema(description = "User's full name", example = "Hien Giang")
  private String name;

  @Schema(
      description = "URL or path to the user avatar",
      example = "https://cdn.devcool.com/avatars/hien.png")
  private String avatar;

  @Schema(
      description = "User's role within the system",
      example = "USER",
      implementation = Role.class)
  private Role role;

  @Schema(
      description = "Current account status",
      example = "ACTIVE",
      implementation = UserStatus.class)
  private UserStatus status;

  @Schema(
      description = "The last time the user logged in (UTC time)",
      type = "string",
      format = "date-time",
      example = "2025-10-12T15:30:00Z")
  private Instant lastLoginTime;

  @Schema(description = "Indicates whether the user's email has been verified", example = "true")
  private boolean emailVerified;
}
