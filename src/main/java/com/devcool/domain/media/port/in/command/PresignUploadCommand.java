package com.devcool.domain.media.port.in.command;

/** The file the client is about to upload: its declared type and exact size get signed. */
public record PresignUploadCommand(Long userId, Long channelId, String contentType, long size) {}
