package com.devcool.domain.media.port.in;

import com.devcool.domain.media.port.in.command.PresignUploadCommand;
import java.time.Instant;

/** Signs a short-lived S3 PUT so the browser uploads directly; the API never sees the bytes. */
public interface PresignUploadUseCase {
  PresignedUpload presignUpload(PresignUploadCommand command);

  /** {@code key} is what the client SENDs afterwards as the media message content. */
  record PresignedUpload(String url, String key, Instant expiresAt) {}
}
