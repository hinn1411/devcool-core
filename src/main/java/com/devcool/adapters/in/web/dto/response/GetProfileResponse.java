package com.devcool.adapters.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
      allowableValues = {"USER", "ADMIN"})
  private String role;

  @Schema(
      description = "Current account status",
      example = "ACTIVE",
      allowableValues = {"ACTIVE", "DELETED", "BLOCKED"})
  private String status;

  @Schema(
      description = "The last time the user logged in (UTC time)",
      type = "string",
      format = "date-time",
      example = "2025-10-12T15:30:00Z")
  private Instant lastLoginTime;

  @Schema(
      description =
          "Email verification state: true = verified, false = attempted but failed,"
              + " null = never attempted",
      example = "true",
      nullable = true)
  private Boolean emailVerified;
}
