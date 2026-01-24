package com.devcool.adapters.in.web.dto.request;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

@Schema(name = "CreateChannelRequest", description = "Channel for creation")
public record CreateChannelRequest(
    @Size(min = 8, max = 255) String name,
    @NotNull BoundaryType boundaryType,
    @FutureOrPresent Instant expiredTime,
    @NotNull ChannelType channelType,
    Integer leaderId,
    @Size(min = 1, max = 10) List<Integer> memberIds) {}
