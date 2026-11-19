package com.devcool.adapters.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The file about to be uploaded. The PUT must then send exactly this Content-Type and size. */
public record PresignUploadRequest(
    @Schema(description = "Channel the media is for; the caller must be a member") @NotNull
        Long channelId,
    @Schema(description = "image/jpeg, image/png, image/webp or video/mp4", example = "image/png")
        @NotBlank
        String contentType,
    @Schema(description = "Exact file size in bytes (signed into the URL)", example = "1024")
        @NotNull
        Long size) {}
