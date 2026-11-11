package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Both required: content is the only editable field (ADR-0014 "missing = keep" n/a). */
@Schema(description = "Edit a TEXT/MARKDOWN message. Both fields are required.")
public record EditMessageRequest(
    @Schema(description = "The new content (1..1000 characters)") @NotNull String content,
    @Schema(description = "The version the client last saw; a stale one gets 409") @NotNull @Min(0)
        Integer version) {}
