package com.devcool.adapters.in.web.dto.request;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(
    name = "UpdateChannelRequest",
    description = "Partial update: a missing field keeps its stored value")
public record UpdateChannelRequest(
    @Size(min = 8, max = 255) String name,
    BoundaryType boundaryType,
    @FutureOrPresent Instant expiredTime,
    @Schema(description = "Must equal the current type") ChannelType channelType) {}
