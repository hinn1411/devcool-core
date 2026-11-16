package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TransferLeaderRequest(
    @Schema(description = "A member of the channel") @NotNull Long userId) {}
