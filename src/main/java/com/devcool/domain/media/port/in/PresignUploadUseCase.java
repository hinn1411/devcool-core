package com.devcool.domain.media.port.in;

import com.devcool.domain.media.port.in.command.PresignUploadCommand;
import java.time.Instant;

/** Signs an S3 PUT so the browser uploads directly. */
public interface PresignUploadUseCase {
  PresignedUpload presignUpload(PresignUploadCommand command);

  /** {@code key} is the IMAGE/VIDEO message content to SEND. */
  record PresignedUpload(String url, String key, Instant expiresAt) {}
}
