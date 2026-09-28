package com.devcool.domain.media.port.in;

import java.time.Instant;

public interface GetMediaUrlUseCase {
  PresignedUrlResult getPresignedUrl(Integer userId, String objectKey);

  record PresignedUrlResult(String url, Instant expiresAt) {}
}
