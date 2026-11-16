package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The member who becomes the forum leader")
public record TransferLeaderRequest(
    @Schema(description = "User id of an existing member of the channel") @NotNull Long userId) {}
