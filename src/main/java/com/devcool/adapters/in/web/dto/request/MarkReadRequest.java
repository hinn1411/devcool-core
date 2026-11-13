package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The highest seq the client has shown to the user")
public record MarkReadRequest(
    @Schema(description = "Message seq, clamped to the channel's last seq") Long seq) {}
