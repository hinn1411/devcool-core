package com.devcool.adapters.web.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "UpdateChannelRequest", description = "Request for updating a channel")
public record AddMembersRequest(
    @Size(min = 1) List<Integer> userIds
) {
}
