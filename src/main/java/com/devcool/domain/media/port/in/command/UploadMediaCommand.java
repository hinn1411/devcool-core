package com.devcool.domain.media.port.in.command;

import java.io.InputStream;

public record UploadMediaCommand(
    InputStream content,
    long size,
    String contentType,
    Integer userId,
    Integer channelId
) { }
