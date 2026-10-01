package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(name = "AddMembersRequest", description = "Request for adding members to a channel")
public record AddMembersRequest(@NotNull @Size(min = 1, max = 50) List<Integer> userIds) {}
