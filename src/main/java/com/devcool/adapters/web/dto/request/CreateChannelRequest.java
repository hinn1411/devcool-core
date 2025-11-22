package com.devcool.adapters.web.dto.request;

import com.devcool.domain.channel.model.enums.BoundaryType;
import com.devcool.domain.channel.model.enums.ChannelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.Instant;

@Schema(name = "CreateChannelRequest", description = "Channel for creation")
public record CreateChannelRequest(@NotBlank @Size(min = 8, max = 255) String name,
                                   @NotNull BoundaryType boundaryType,
                                   @FutureOrPresent Instant expiredTime,
                                   @NotNull ChannelType channelType,
                                   String leader) {
}
