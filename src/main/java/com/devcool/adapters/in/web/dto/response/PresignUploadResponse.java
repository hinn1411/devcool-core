package com.devcool.adapters.in.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PresignUploadResponse(
    @Schema(description = "PUT the file here with the declared Content-Type and size") String url,
    @Schema(description = "Send this as the IMAGE/VIDEO message content") String key,
    Instant expiresAt) {}
