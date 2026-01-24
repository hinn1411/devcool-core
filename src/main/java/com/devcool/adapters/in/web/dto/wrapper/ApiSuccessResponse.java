package com.devcool.adapters.in.web.dto.wrapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"status", "code", "message", "data", "meta", "timestamp"})
@Schema(description = "Standard success response wrapper")
public class ApiSuccessResponse<T> {

  @Schema(description = "HTTP status code", example = "200")
  private int status;

  @Schema(description = "Application success code", example = "USR_200")
  private String code;

  @Schema(description = "Human-readable message", example = "Get user profile successfully")
  private String message;

  @Schema(description = "Actual payload (varies by endpoint)")
  private T data;

  @Schema(
      description = "Optional metadata (e.g., pagination)",
      additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> meta;

  @Builder.Default
  @Schema(
      description = "Server time (UTC)",
      type = "string",
      format = "date-time",
      example = "2025-10-12T15:30:00Z")
  private Instant timestamp = Instant.now();
}
