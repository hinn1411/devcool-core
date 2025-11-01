package com.devcool.adapters.web.dto.wrapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@Schema(name = "ApiErrorResponse", description = "Standard error response structure for failed API calls")
public class ApiErrorResponse {

    @Schema(
            description = "Application-specific error code",
            example = "USR_409_EMAIL"
    )
    private String code;

    @Schema(
            description = "Human-readable error message describing what went wrong",
            example = "Email already in use"
    )
    private String message;

    @Schema(
            description = "Additional error details (e.g., which field or parameter caused the error)",
            additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
            example = "{ \"email\": \"hien@example.com\" }"
    )
    private Map<String, Object> details;

    @Schema(
            description = "Timestamp when the error occurred (UTC time)",
            type = "string",
            format = "date-time",
            example = "2025-10-12T15:30:00Z"
    )
    private Instant timestamp;

    @Schema(
            description = "HTTP status code associated with the error",
            example = "409"
    )
    private int status;
}