package com.devcool.domain.media.port.in.command;

import java.io.IOException;
import java.io.InputStream;

/**
 * What the media use case needs from an upload, with no transport type in it: a WebSocket upload, a
 * batch import or a queue consumer can drive it as well as HTTP multipart.
 */
public record UploadMediaCommand(
    String filename,
    String contentType,
    long size,
    Content content,
    Integer userId,
    Integer channelId) {

  /** Opens the upload's bytes. Called once, after validation, by the service. */
  @FunctionalInterface
  public interface Content {
    InputStream open() throws IOException;
  }
}
