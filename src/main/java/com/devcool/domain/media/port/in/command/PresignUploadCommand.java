package com.devcool.domain.media.port.in.command;

/** Declared type and exact size; both get signed. */
public record PresignUploadCommand(Long userId, Long channelId, String contentType, long size) {}
