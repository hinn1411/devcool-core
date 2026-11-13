package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "The highest seq the client has shown to the user")
public record MarkReadRequest(
    @Schema(description = "Message seq; clamped to the channel's last seq, never moves back")
        @NotNull
        @PositiveOrZero
        Long seq) {}
