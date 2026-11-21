package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OpenDirectChatRequest(
    @Schema(description = "The other user; not the caller") @NotNull Long userId) {}
