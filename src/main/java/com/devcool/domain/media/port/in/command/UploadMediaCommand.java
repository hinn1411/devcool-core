package com.devcool.domain.media.port.in.command;

import org.springframework.web.multipart.MultipartFile;

public record UploadMediaCommand(
    MultipartFile file,
    long size,
    String contentType,
    Integer userId,
    Integer channelId
) { }
